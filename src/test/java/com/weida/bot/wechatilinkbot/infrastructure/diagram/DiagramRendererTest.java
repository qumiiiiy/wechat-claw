package com.weida.bot.wechatilinkbot.infrastructure.diagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link DiagramRenderer} by swapping the configured executable for a
 * launcher script that delegates to a tiny Java stub. The stub respects the
 * {@code STUB_MODE} environment variable (and {@code -version} for the
 * availability probe) so each test scenario can be reproduced without
 * installing the real PlantUML CLI.
 */
class DiagramRendererTest {

    private static final byte[] PNG_MAGIC = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    void commandProducesExpectedArgv() {
        DiagramProperties properties = new DiagramProperties();
        properties.setExecutable("plantuml");
        DiagramRenderer renderer = new DiagramRenderer(properties);

        Path sourceFile = Path.of("/tmp/source.puml");
        assertThat(renderer.command(sourceFile)).containsExactly(
                "plantuml",
                "-tpng",
                "-charset",
                "UTF-8",
                "-failfast2",
                sourceFile.toAbsolutePath().toString());
    }

    @Test
    void reportsUnavailableWhenExecutableIsBlank() {
        DiagramProperties properties = new DiagramProperties();
        properties.setExecutable("");
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThat(renderer.isAvailable()).isFalse();
        assertThatThrownBy(() -> renderer.render("ignored"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_EXECUTABLE_MISSING));
    }

    @Test
    void resolvesPlantUmlLauncherWhenConfiguredValueIsItsDirectory(@TempDir Path tempDir)
            throws Exception {
        Path stubLauncher = writeLauncher(
                tempDir,
                buildStubClasses(tempDir),
                "ok");
        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        Path directory = tempDir.resolve("plantuml-home");
        Files.createDirectories(directory);
        Path configuredLauncher = directory.resolve(windows ? "plantuml.bat" : "plantuml");
        Files.copy(stubLauncher, configuredLauncher, StandardCopyOption.REPLACE_EXISTING);
        if (!windows) {
            configuredLauncher.toFile().setExecutable(true);
        }

        DiagramProperties properties = new DiagramProperties();
        properties.setEnabled(true);
        properties.setExecutable(directory.toString());
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThat(renderer.isAvailable()).isTrue();
        assertThat(renderer.command(Path.of("source.puml")).get(0))
                .isEqualTo(configuredLauncher.toString());
    }

    @Test
    void rendersPngViaStubExecutable(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "ok");
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThat(renderer.isAvailable()).isTrue();
        RenderResult result = renderer.render("@startuml\nAlice -> Bob\n@enduml\n");

        assertThat(result.fileName()).isEqualTo("diagram.png");
        assertThat(result.pngBytes()).startsWith(PNG_MAGIC);
        assertThat(result.pngBytes().length).isGreaterThan(PNG_MAGIC.length);
    }

    @Test
    void surfacesNonZeroExitAsProcessFailed(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "fail");
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_PROCESS_FAILED));
    }

    @Test
    void surfacesTimeoutWhenStubSleeps(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "slow");
        properties.setTimeout(Duration.ofMillis(500));
        properties.setRetryAttempts(1);
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_TIMEOUT));
    }

    @Test
    void retriesUntilMaxAttemptsThenTimesOut(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "slow");
        properties.setTimeout(Duration.ofMillis(300));
        properties.setRetryAttempts(3);
        properties.setRetryDelay(Duration.ofMillis(50));
        DiagramRenderer renderer = new DiagramRenderer(properties);

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_TIMEOUT));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        // 3 attempts at 300ms each = 900ms minimum, plus retry delays of
        // 50ms, 100ms. Anything well above 900ms confirms the retry loop
        // executed more than once.
        assertThat(elapsedMillis).isGreaterThan(900L);
    }

    @Test
    void retriesOnOutputInvalidAsWellAsTimeout(@TempDir Path tempDir) throws Exception {
        // The "junk" stub exits 0 but writes non-PNG bytes — the renderer
        // sees DIAGRAM_OUTPUT_INVALID after waitForValidPng. With retry
        // enabled the loop must run more than once before giving up.
        DiagramProperties properties = baseProperties(tempDir, "junk");
        properties.setTimeout(Duration.ofMillis(250));
        properties.setRetryAttempts(3);
        properties.setRetryDelay(Duration.ofMillis(50));
        DiagramRenderer renderer = new DiagramRenderer(properties);

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_OUTPUT_INVALID));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        // 3 attempts plus 50ms + 100ms delays = well above a single
        // sub-second pass; confirms retry fired at least once.
        assertThat(elapsedMillis).isGreaterThan(100L);
    }

    @Test
    void destroysProcessTreeWhenThreadIsInterrupted(@TempDir Path tempDir)
            throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "slow");
        properties.setTimeout(Duration.ofSeconds(30));
        DiagramRenderer renderer = new DiagramRenderer(properties);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                    .isInstanceOf(DiagramException.class)
                    .satisfies(error -> assertThat(((DiagramException) error).getCode())
                            .isEqualTo(DiagramErrorCode.DIAGRAM_TIMEOUT));
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsOutputLargerThanLimit(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "big");
        properties.setMaxOutputBytes(PNG_MAGIC.length + 8);
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_OUTPUT_TOO_LARGE));
    }

    @Test
    void rejectsOutputWithoutPngMagic(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "junk");
        properties.setTimeout(Duration.ofMillis(250));
        properties.setRetryAttempts(1);
        DiagramRenderer renderer = new DiagramRenderer(properties);

        assertThatThrownBy(() -> renderer.render("@startuml\nX\n@enduml"))
                .isInstanceOf(DiagramException.class)
                .satisfies(error -> assertThat(((DiagramException) error).getCode())
                        .isEqualTo(DiagramErrorCode.DIAGRAM_OUTPUT_INVALID));
    }

    @Test
    void waitsForPngFileToAppearAfterProcessExit(@TempDir Path tempDir) throws Exception {
        DiagramProperties properties = baseProperties(tempDir, "late");
        DiagramRenderer renderer = new DiagramRenderer(properties);

        RenderResult result = renderer.render("@startuml\nX\n@enduml");

        assertThat(result.fileName()).isEqualTo("diagram.png");
        assertThat(result.pngBytes()).startsWith(PNG_MAGIC);
    }

    private static DiagramProperties baseProperties(Path tempDir, String mode)
            throws IOException, InterruptedException {
        Path stubClasses = buildStubClasses(tempDir);
        Path launcher = writeLauncher(tempDir, stubClasses, mode);
        DiagramProperties properties = new DiagramProperties();
        properties.setExecutable(launcher.toString());
        properties.setEnabled(true);
        return properties;
    }

    private static Path buildStubClasses(Path tempDir)
            throws IOException, InterruptedException {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path stubSource = srcDir.resolve("PumlStub.java");
        Files.writeString(stubSource, stubSourceCode());
        Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes);
        runProcess(srcDir, "javac", "-d", classes.toString(),
                stubSource.toString());
        return classes;
    }

    private static Path writeLauncher(Path tempDir, Path classes, String mode)
            throws IOException {
        boolean windows = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win");
        Path launcher;
        if (windows) {
            launcher = tempDir.resolve("plantuml-stub-" + mode + ".cmd");
            String javaBin = System.getProperty("java.home")
                    + "\\bin\\java.exe";
            Files.writeString(launcher,
                            "@echo off\r\n"
                            + "set STUB_MODE=" + mode + "\r\n"
                            + "\"" + javaBin + "\" -cp \"" + classes.toString()
                            + "\" stub.PumlStub %*\r\n");
        } else {
            launcher = tempDir.resolve("plantuml-stub-" + mode + ".sh");
            Files.writeString(launcher,
                    "#!/bin/sh\n"
                    + "export STUB_MODE=" + mode + "\n"
                    + "java -cp '" + classes.toString()
                            + "' stub.PumlStub \"$@\"\n");
            launcher.toFile().setExecutable(true);
        }
        return launcher;
    }

    private static void runProcess(Path cwd, String... argv)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(argv)
                .directory(cwd.toFile())
                .start();
        int exit = process.waitFor();
        if (exit != 0) {
            byte[] err = process.getErrorStream().readAllBytes();
            throw new IOException("process failed: " + argv[0]
                    + " exit=" + exit + " stderr="
                    + new String(err, StandardCharsets.UTF_8));
        }
    }

    private static String stubSourceCode() {
        return "package stub;\n"
                + "import java.io.*;\n"
                + "import java.nio.file.*;\n"
                + "public final class PumlStub {\n"
                + "    private static final byte[] MAGIC = new byte[]{\n"
                + "        (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A};\n"
                + "    public static void main(String[] args) throws Exception {\n"
                + "        for (String arg : args) {\n"
                + "            if (\"-version\".equals(arg)) {\n"
                + "                System.out.println(\"PumlStub version 1.0\");\n"
                + "                return;\n"
                + "            }\n"
                + "        }\n"
                + "        // In file mode the last argument is the source .puml file.\n"
                + "        // PlantUML writes a sibling <name>.png so we mirror that here.\n"
                + "        String sourcePath = args[args.length - 1];\n"
                + "        String outputPath = sourcePath.replaceFirst(\"\\\\.puml$\", \".png\");\n"
                + "        String mode = System.getenv().getOrDefault(\"STUB_MODE\",\"ok\");\n"
                + "        switch (mode) {\n"
                + "            case \"ok\" -> emitFile(outputPath, 16);\n"
                + "            case \"big\" -> emitFile(outputPath, 1024);\n"
                + "            case \"junk\" -> {\n"
                + "                Files.write(Paths.get(outputPath),\n"
                + "                        new byte[]{'n','o','t','p','n','g'});\n"
                + "            }\n"
                + "            case \"fail\" -> {\n"
                + "                System.err.println(\"stub failure\");\n"
                + "                System.exit(7);\n"
                + "            }\n"
                + "            case \"slow\" -> {\n"
                + "                try { Thread.sleep(3000); }\n"
                + "                catch (InterruptedException e) { System.exit(130); }\n"
                + "                emitFile(outputPath, 8);\n"
                + "            }\n"
                + "            case \"late\" -> {\n"
                + "                // Simulate a writer that completes after a short delay\n"
                + "                // (mirrors the Windows/Chocolatey flush race).\n"
                + "                new Thread(() -> {\n"
                + "                    try { Thread.sleep(200); }\n"
                + "                    catch (InterruptedException e) { return; }\n"
                + "                    try { emitFile(outputPath, 16); }\n"
                + "                    catch (IOException ignored) {}\n"
                + "                }).start();\n"
                + "            }\n"
                + "            default -> System.exit(2);\n"
                + "        }\n"
                + "    }\n"
                + "    private static void emitFile(String path, int extra) throws IOException {\n"
                + "        try (OutputStream out = Files.newOutputStream(Paths.get(path))) {\n"
                + "            out.write(MAGIC);\n"
                + "            byte[] pad = new byte[extra];\n"
                + "            for (int i = 0; i < pad.length; i++) {\n"
                + "                pad[i] = (byte) (i & 0xff);\n"
                + "            }\n"
                + "            out.write(pad);\n"
                + "            out.flush();\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }
}
