package com.weida.bot.wechatilinkbot.infrastructure.code;

import com.weida.bot.wechatilinkbot.model.TestResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Runs pre-configured test commands in a repository.
 *
 * <p>Only runs the fixed test command from configuration.
 * No shell拼接, no arbitrary programs, no auto-commit, no auto-cleanup.
 * Returns structured test summary without exposing unbounded logs,
 * environment variables, or absolute paths.</p>
 */
@Component
public class TestRunner {

    private static final int MAX_CAPTURED_OUTPUT_BYTES = 64 * 1024;
    private static final int READ_BUFFER_BYTES = 4096;
    private static final long OUTPUT_POLL_MILLIS = 20L;

    private final CodeWorkspaceProperties properties;

    public TestRunner(CodeWorkspaceProperties properties) {
        this.properties = properties;
    }

    /**
     * Runs the configured test command in the repository.
     *
     * @param repoRoot the repository root directory
     * @return structured test result
     * @throws IllegalArgumentException if the test command is not configured
     */
    public TestResult run(Path repoRoot) {
        String testCommand = properties.getTestCommand();
        if (testCommand == null || testCommand.isBlank()) {
            throw new IllegalArgumentException("测试命令未配置");
        }

        long startTime = System.currentTimeMillis();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        List<String> failures = new ArrayList<>();
        AtomicInteger exitCode = new AtomicInteger(-1);

        try {
            String[] command = testCommand.strip().split("\\s+");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(repoRoot.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            long timeoutMs = properties.getTestTimeout().toMillis();
            byte[] capturedOutput = drainProcessOutput(process, timeoutMs);
            if (process.isAlive()) {
                timedOut.set(true);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            } else {
                exitCode.set(process.exitValue());
                // On Windows the wrapper (mvnw.cmd) can exit while its child JVM still holds
                // repository files for a few moments. Give grandchildren a bounded grace
                // period so callers can clean up the repository directory reliably.
                TimeUnit.MILLISECONDS.sleep(500);
            }
            collectFailures(new String(capturedOutput, StandardCharsets.UTF_8), failures);
        } catch (IOException e) {
            throw new IllegalArgumentException("执行测试命令失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("测试执行被中断", e);
        }

        long durationMs = System.currentTimeMillis() - startTime;

        int totalTests = 0;
        int passedCount = 0;
        int failedCount = 0;

        if (!timedOut.get() && exitCode.get() == 0) {
            passedCount = 1;
            totalTests = 1;
        } else if (!timedOut.get() && exitCode.get() != 0) {
            failedCount = failures.size();
            totalTests = failedCount;
        }

        return new TestResult(
                !timedOut.get() && exitCode.get() == 0,
                totalTests,
                passedCount,
                failedCount,
                List.copyOf(failures),
                durationMs,
                timedOut.get());
    }

    private byte[] drainProcessOutput(Process process, long timeoutMs)
            throws IOException, InterruptedException {
        InputStream output = process.getInputStream();
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        byte[] captured = new byte[MAX_CAPTURED_OUTPUT_BYTES];
        int capturedBytes = 0;
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        while (process.isAlive() && System.nanoTime() < deadlineNanos) {
            while (output.available() > 0) {
                int read = output.read(buffer);
                if (read < 0) {
                    break;
                }
                int copyBytes = Math.min(read, captured.length - capturedBytes);
                if (copyBytes > 0) {
                    System.arraycopy(buffer, 0, captured, capturedBytes, copyBytes);
                    capturedBytes += copyBytes;
                }
            }
            if (process.isAlive()) {
                TimeUnit.MILLISECONDS.sleep(OUTPUT_POLL_MILLIS);
            }
        }
        while (output.available() > 0) {
            int read = output.read(buffer);
            if (read < 0) {
                break;
            }
            int copyBytes = Math.min(read, captured.length - capturedBytes);
            if (copyBytes > 0) {
                System.arraycopy(buffer, 0, captured, capturedBytes, copyBytes);
                capturedBytes += copyBytes;
            }
        }
        return java.util.Arrays.copyOf(captured, capturedBytes);
    }

    private static void collectFailures(String output, List<String> failures) {
        output.lines()
                .filter(line -> line.contains("FAIL")
                        || line.contains("Error")
                        || line.contains("Exception"))
                .map(String::strip)
                .limit(10)
                .forEach(failures::add);
    }
}
