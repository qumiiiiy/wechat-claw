package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invokes the PlantUML CLI as a bounded child process.
 *
 * <p>GPL compliance note: this class only spawns an external {@code plantuml}
 * process and exchanges stdin / stdout. No PlantUML Java classes are
 * statically or dynamically linked into this module.</p>
 *
 * <p>The executable name is sourced exclusively from
 * {@link DiagramProperties#getExecutable()}; the model never controls it.
 * Source text is fed via stdin and PNG output is captured via stdout
 * redirected to a temporary file so memory stays bounded. The process
 * working directory is a fresh per-invocation temporary directory so no
 * PlantUML artefact leaks into the JVM launch directory.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Component
public class DiagramRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(DiagramRenderer.class);
    private static final long EXECUTABLE_CHECK_TIMEOUT_SECONDS = 5L;
    private static final long POST_DESTROY_WAIT_SECONDS = 5L;
    private static final int PROCESS_LOG_MAX_CHARACTERS = 1000;
    private static final String TEMP_PREFIX = "claw-puml-";
    private static final String SOURCE_FILE_NAME = "source.puml";
    private static final String OUTPUT_FILE_NAME = "source.png";
    private static final long OUTPUT_POLL_INTERVAL_MILLIS = 50L;
    private static final byte[] PNG_MAGIC = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final DiagramProperties properties;
    private final String executable;
    private final boolean executableAvailable;

    public DiagramRenderer(DiagramProperties properties) {
        this.properties = properties;
        this.executable = resolveExecutable(properties.getExecutable());
        // Disabled capabilities must not start an external process during application
        // startup (or every test context construction).
        this.executableAvailable = properties.isEnabled() && probe(executable);
        if (!executableAvailable && properties.isEnabled()) {
            LOG.warn(
                    "diagram_executable_missing executable={} configuration=PLANTUML_EXECUTABLE",
                    executable);
        }
    }

    /** Reports whether the configured executable can be invoked. */
    public boolean isAvailable() {
        return executableAvailable;
    }

    /**
     * Renders the supplied source into a PNG using the configured PlantUML
     * executable in {@code -pipe} mode.
     *
     * @param source PlantUML source text already validated by
     *               {@link PlantumlSourceGuard}
     * @return PNG payload and a safe file name
     * @throws DiagramException on timeout, process failure, or invalid output
     */
    public RenderResult render(String source) {
        if (!executableAvailable) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_EXECUTABLE_MISSING,
                    "PlantUML 可执行文件不可用：" + executable);
        }
        int maxAttempts = properties.getRetryAttempts();
        Duration baseDelay = properties.getRetryDelay();
        DiagramException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return attemptRender(source);
            } catch (DiagramException exception) {
                String code = exception.getCode();
                boolean retryable = DiagramErrorCode.DIAGRAM_TIMEOUT.equals(code)
                        || DiagramErrorCode.DIAGRAM_OUTPUT_INVALID.equals(code);
                if (!retryable || attempt == maxAttempts) {
                    throw exception;
                }
                lastException = exception;
                long delayMillis = Math.min(
                        baseDelay.toMillis() * (1L << (attempt - 1)),
                        baseDelay.toMillis() * 4L);
                LOG.warn(
                        "diagram_render_retry attempt={} maxAttempts={} delayMillis={} code={}",
                        attempt, maxAttempts, delayMillis, code);
                try {
                    TimeUnit.MILLISECONDS.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new DiagramException(
                            DiagramErrorCode.DIAGRAM_TIMEOUT,
                            "PlantUML 渲染重试被中断");
                }
            }
        }
        // Loop always either returns or throws; this line is unreachable.
        throw lastException;
    }

    private RenderResult attemptRender(String source) {
        Path directory = null;
        Path sourceFile = null;
        Path outputFile = null;
        Path logFile = null;
        try {
            directory = Files.createTempDirectory(TEMP_PREFIX);
            sourceFile = directory.resolve(SOURCE_FILE_NAME);
            outputFile = directory.resolve(OUTPUT_FILE_NAME);
            logFile = directory.resolve("render.log");
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

            ProcessResult result = run(command(sourceFile), logFile);
            if (result.exitCode() != 0) {
                throw new DiagramException(
                        DiagramErrorCode.DIAGRAM_PROCESS_FAILED,
                        "PlantUML 退出码非零：" + result.exitCode() + "："
                                + abbreviate(result.output(),
                                        PROCESS_LOG_MAX_CHARACTERS));
            }
            // PlantUML on some hosts (notably the Windows/Chocolatey build)
            // can finish the JVM process before the OS flushes the PNG file,
            // so wait up to the configured timeout for the artefact to land.
            if (!waitForValidPng(outputFile)) {
                throw new DiagramException(
                        DiagramErrorCode.DIAGRAM_OUTPUT_INVALID,
                        "PlantUML 未生成合法 PNG 输出");
            }
            long size = Files.size(outputFile);
            if (size > properties.getMaxOutputBytes()) {
                throw new DiagramException(
                        DiagramErrorCode.DIAGRAM_OUTPUT_TOO_LARGE,
                        "PlantUML 输出超过大小上限：" + size + " > "
                                + properties.getMaxOutputBytes());
            }
            byte[] png = Files.readAllBytes(outputFile);
            return new RenderResult(png, "diagram.png");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_TIMEOUT,
                    "PlantUML 渲染被中断");
        } catch (IOException exception) {
            throw new DiagramException(
                    DiagramErrorCode.DIAGRAM_PROCESS_FAILED,
                    "无法执行 PlantUML：" + exception.getMessage(),
                    exception);
        } finally {
            deleteQuietly(sourceFile);
            deleteQuietly(outputFile);
            deleteQuietly(logFile);
            deleteQuietly(directory);
        }
    }

    /** Builds the argv list. Visible for tests. */
    List<String> command(Path sourceFile) {
        List<String> argv = new ArrayList<>(8);
        argv.add(executable);
        argv.add("-tpng");
        argv.add("-charset");
        argv.add("UTF-8");
        argv.add("-failfast2");
        argv.add(sourceFile.toAbsolutePath().toString());
        return argv;
    }

    private ProcessResult run(
            List<String> command,
            Path logFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(logFile.getParent().toFile())
                .redirectError(logFile.toFile())
                .start();
        try {
            boolean finished = process.waitFor(
                    properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                throw new DiagramException(
                        DiagramErrorCode.DIAGRAM_TIMEOUT,
                        "PlantUML 渲染超时：" + properties.getTimeout());
            }
            String error = Files.exists(logFile)
                    ? Files.readString(logFile, StandardCharsets.UTF_8)
                    : "";
            return new ProcessResult(process.exitValue(), error.strip());
        } catch (InterruptedException exception) {
            destroyProcessTree(process);
            throw exception;
        }
    }

    /**
     * Polls for the output PNG to exist and start with the PNG magic bytes.
     * Returns {@code false} if the wait budget elapses without a valid file.
     */
    private boolean waitForValidPng(Path outputFile) throws InterruptedException {
        long deadline = System.nanoTime()
                + properties.getTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(outputFile)) {
                try {
                    long size = Files.size(outputFile);
                    if (size >= PNG_MAGIC.length) {
                        byte[] head = new byte[PNG_MAGIC.length];
                        try (var in = Files.newInputStream(outputFile)) {
                            int read = in.read(head);
                            if (read == PNG_MAGIC.length
                                    && Arrays.equals(head, PNG_MAGIC)) {
                                return true;
                            }
                        }
                    }
                } catch (IOException ignored) {
                    // File may be transiently locked by the writer; retry.
                }
            }
            TimeUnit.MILLISECONDS.sleep(OUTPUT_POLL_INTERVAL_MILLIS);
        }
        return false;
    }

    private boolean probe(String executable) {
        if (executable == null || executable.isBlank()) {
            return false;
        }
        Process process = null;
        try {
            process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(
                    EXECUTABLE_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                destroyProcessTree(process);
            }
        }
    }

    private static String resolveExecutable(String configuredExecutable) {
        if (configuredExecutable == null || configuredExecutable.isBlank()) {
            return "";
        }
        try {
            Path configuredPath = Path.of(configuredExecutable);
            if (!Files.isDirectory(configuredPath)) {
                return configuredExecutable;
            }
            boolean windows = System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT)
                    .contains("win");
            List<String> candidates = windows
                    ? List.of("plantuml.bat", "plantuml.cmd", "plantuml")
                    : List.of("plantuml", "plantuml.sh", "plantuml.bat");
            for (String candidate : candidates) {
                Path executablePath = configuredPath.resolve(candidate);
                if (Files.isRegularFile(executablePath)) {
                    return executablePath.toString();
                }
            }
        } catch (InvalidPathException ignored) {
            // Probe the original value so the normal unavailable reason is preserved.
        }
        return configuredExecutable;
    }

    /**
     * Kills the process and every descendant so launcher scripts that fork
     * a JVM child cannot leak past the configured timeout.
     */
    private static void destroyProcessTree(Process process) {
        if (process == null) {
            return;
        }
        List<ProcessHandle> processTree;
        try {
            processTree = Stream.concat(
                    Stream.of(process.toHandle()),
                    process.toHandle().descendants())
                    .toList()
                    .reversed();
        } catch (RuntimeException ignored) {
            processTree = List.of(process.toHandle());
        }
        processTree.forEach(handle -> {
            try {
                handle.destroyForcibly();
            } catch (RuntimeException ignored) {
                // best-effort cleanup
            }
        });
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(POST_DESTROY_WAIT_SECONDS);
        for (ProcessHandle handle : processTree) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !handle.isAlive()) {
                continue;
            }
            try {
                handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (TimeoutException | java.util.concurrent.ExecutionException ignored) {
                // best-effort cleanup; do not mask the original failure
            }
        }
    }

    private static boolean startsWithPngMagic(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_MAGIC.length) {
            return false;
        }
        for (int index = 0; index < PNG_MAGIC.length; index++) {
            if (bytes[index] != PNG_MAGIC[index]) {
                return false;
            }
        }
        return true;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("diagram_temp_delete_failed path={}", path, exception);
            }
        }
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalised = value.strip();
        if (normalised.length() <= maxLength) {
            return normalised;
        }
        return normalised.substring(0, maxLength) + "…";
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
