package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetReadException;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetWriteException;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetReadResult;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests covering filesystem boundary, atomic write, verification,
 * read pipeline, and append-with-copy semantics for {@link SpreadsheetCapability}.
 */
class SpreadsheetCapabilityTest {

    @TempDir
    Path outputDirectory;

    @Test
    void writesAndReopensInsideControlledDirectory() throws Exception {
        SpreadsheetCapability capability = enabledCapability();

        SpreadsheetResult result = capability.generate(new SpreadsheetRequest(
                "课程任务清单",
                null,
                List.of(SpreadsheetColumn.of("序号"), SpreadsheetColumn.of("任务")),
                List.of(
                        List.of("1", "算法作业"),
                        List.of("2", "英语阅读"))));

        assertThat(result.filePath()).isNotNull();
        assertThat(result.filePath().getParent().toRealPath())
                .isEqualTo(outputDirectory.toRealPath());
        assertThat(result.filePath().getFileName().toString()).endsWith(".xlsx");
        assertThat(result.verifiedHeaders())
                .containsExactly("序号", "任务");
        assertThat(result.verifiedRowCount()).isEqualTo(2);
        assertThat(result.sheetName()).isEqualTo("Sheet1");
        assertThat(Files.exists(result.filePath())).isTrue();
        assertThat(result.bytes()).isNotEmpty();

        try (InputStream in = Files.newInputStream(result.filePath());
                XSSFWorkbook reopened = new XSSFWorkbook(in)) {
            assertThat(reopened.getNumberOfSheets()).isEqualTo(1);
            assertThat(reopened.getSheetName(0)).isEqualTo("Sheet1");
            assertThat(reopened.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("序号");
            assertThat(reopened.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("算法作业");
            assertThat(reopened.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        }
    }

    @Test
    void generateMediaReturnsChannelReadyPayload() {
        SpreadsheetCapability capability = enabledCapability();

        GeneratedMedia media = capability.generateMedia(new SpreadsheetRequest(
                "频道返回验证",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("x"))));

        assertThat(media.kind()).isEqualTo(GeneratedMediaKind.FILE);
        assertThat(media.fileName()).endsWith(".xlsx");
        assertThat(media.bytes()).isNotEmpty();
        assertThat(media.bytes().length).isGreaterThan(0);
    }

    @Test
    void cannotEscapeOutputDirectoryViaTitle() {
        SpreadsheetCapability capability = enabledCapability();

        SpreadsheetResult result = capability.generate(new SpreadsheetRequest(
                "../etc/passwd",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        Path canonical = result.filePath().toAbsolutePath().normalize();
        Path canonicalRoot = outputDirectory.toAbsolutePath().normalize();
        assertThat(canonical.startsWith(canonicalRoot)).isTrue();
        assertThat(result.filePath().getFileName().toString()).doesNotContain("/");
        assertThat(result.filePath().getFileName().toString()).doesNotContain("..");
    }

    @Test
    void rejectsBlankTitle() {
        SpreadsheetCapability capability = enabledCapability();

        assertThatThrownBy(() -> capability.generate(new SpreadsheetRequest(
                "",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateColumnHeaders() {
        SpreadsheetCapability capability = enabledCapability();

        assertThatThrownBy(() -> capability.generate(new SpreadsheetRequest(
                "dup",
                null,
                List.of(SpreadsheetColumn.of("Id"), SpreadsheetColumn.of("ID")),
                List.of())))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void rejectsRowsExceedingLimit() {
        SpreadsheetCapability capability = smallRowLimitCapability();

        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(List.of(String.valueOf(i)));
        }

        assertThatThrownBy(() -> capability.generate(new SpreadsheetRequest(
                "rows",
                null,
                List.of(SpreadsheetColumn.of("i")),
                rows)))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("行数");
    }

    @Test
    void rejectsCapabilityDisabled() {
        SpreadsheetCapability capability = disabledCapability();

        assertThatThrownBy(() -> capability.generate(new SpreadsheetRequest(
                "off",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1")))))
                .isInstanceOf(SpreadsheetWriteException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void doesNotProduceSideFilesForFailedGeneration(@TempDir Path sandbox) throws IOException {
        SpreadsheetCapability capability = capabilityWithOutput(sandbox);

        try {
            capability.generate(new SpreadsheetRequest(
                    "fail",
                    null,
                    List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("A")),
                    List.of()));
        } catch (SpreadsheetWriteException ignored) {
            // expected
        }

        try (Stream<Path> stream = Files.list(sandbox)) {
            assertThat(stream.findAny()).isEmpty();
        }
    }

    @Test
    void noTempFilesLeftBehindAfterSuccessfulGeneration() throws IOException {
        SpreadsheetCapability capability = enabledCapability();

        capability.generate(new SpreadsheetRequest(
                "清理验证",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        List<Path> entries;
        try (Stream<Path> stream = Files.list(outputDirectory)) {
            entries = stream.toList();
        }
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getFileName().toString()).doesNotEndWith(".tmp");
    }

    @Test
    void writesIntoTargetPathNotSiblingTemp() throws Exception {
        SpreadsheetCapability capability = enabledCapability();

        SpreadsheetResult result = capability.generate(new SpreadsheetRequest(
                "原子落盘",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        // The target path must be the *only* file in the directory; no
        // .tmp siblings left behind.
        assertThat(result.filePath().getFileName().toString())
                .doesNotEndWith(".tmp");
        try (Stream<Path> stream = Files.list(outputDirectory)) {
            long total = stream.count();
            assertThat(total).isEqualTo(1);
        }
        assertThat(Files.exists(result.filePath())).isTrue();
    }

    @Test
    void writeTimeoutEnforcedAndTempCleanedUp(@TempDir Path sandbox) throws IOException {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(sandbox);
        properties.setWriteTimeout(java.time.Duration.ofMillis(1));
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        executor.submit(() -> {
            blockerStarted.countDown();
            releaseBlocker.await();
            return null;
        });

        SpreadsheetCapability capability = new SpreadsheetCapability(renderer, properties, executor);
        try {
            assertThat(blockerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> capability.generate(new SpreadsheetRequest(
                    "timeout",
                    null,
                    List.of(SpreadsheetColumn.of("A")),
                    List.of(List.of("x")))))
                    .isInstanceOf(SpreadsheetWriteException.class)
                    .hasMessageContaining("超时");

            // No temp file should survive, and no target file should exist.
            try (Stream<Path> stream = Files.list(sandbox)) {
                assertThat(stream).isEmpty();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待测试执行器被中断", exception);
        } finally {
            releaseBlocker.countDown();
            capability.shutdown();
        }
    }

    @Test
    void readReturnsVerifiedSummaryForTrustedFile() throws Exception {
        SpreadsheetCapability capability = enabledCapability();

        SpreadsheetResult generated = capability.generate(new SpreadsheetRequest(
                "读取验证",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("v"))));

        SpreadsheetReadResult read = capability.read(generated.filePath());
        assertThat(read.sourcePath()).isEqualTo(generated.filePath());
        assertThat(read.totalBytes()).isEqualTo(Files.size(generated.filePath()));
        assertThat(read.sheets()).hasSize(1);
        assertThat(read.sheets().get(0).headers()).containsExactly("A");
        assertThat(read.sheets().get(0).rowCount()).isEqualTo(1);
    }

    @Test
    void readRejectsXlsmFile() throws Exception {
        SpreadsheetCapability capability = enabledCapability();
        Path xlsm = outputDirectory.resolve("macro.xlsm");
        Files.writeString(xlsm, "PK-fake");

        assertThatThrownBy(() -> capability.read(xlsm))
                .isInstanceOf(SpreadsheetReadException.class)
                .hasMessageContaining(".xlsm");
    }

    @Test
    void readRejectsMissingFile() {
        SpreadsheetCapability capability = enabledCapability();
        assertThatThrownBy(() -> capability.read(outputDirectory.resolve("missing.xlsx")))
                .isInstanceOf(SpreadsheetReadException.class);
    }

    @Test
    void appendSheetPreservesSourceBytesAndAddsSecondSheet(@TempDir Path sandbox) throws Exception {
        SpreadsheetCapability capability = capabilityWithOutput(sandbox);

        SpreadsheetResult original = capability.generate(new SpreadsheetRequest(
                "原始表",
                "原始",
                List.of(SpreadsheetColumn.of("A"), SpreadsheetColumn.of("B")),
                List.of(List.of("1", "2"))));

        byte[] sourceSnapshot = Files.readAllBytes(original.filePath());
        String sourceDigestBefore = sha256Hex(sourceSnapshot);

        SpreadsheetCapability.AppendResult appended = capability.appendSheet(
                original.filePath(),
                new SpreadsheetAppendRequest(
                        "新增",
                        List.of(SpreadsheetColumn.of("X"), SpreadsheetColumn.of("Y")),
                        List.of(List.of("a", "b"))));

        // 1. Source bytes unchanged.
        byte[] sourceAfter = Files.readAllBytes(original.filePath());
        assertThat(sourceAfter).isEqualTo(sourceSnapshot);
        assertThat(sha256Hex(sourceAfter)).isEqualTo(sourceDigestBefore);

        // 2. AppendResult reports the source digest so callers can verify.
        assertThat(appended.sourceSha256Hex()).isEqualTo(sourceDigestBefore);

        // 3. The destination is a separate file with the appended sheet.
        assertThat(appended.result().filePath()).isNotEqualTo(original.filePath());
        try (InputStream in = Files.newInputStream(appended.result().filePath());
                XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetName(0)).isEqualTo("原始");
            assertThat(wb.getSheetName(1)).isEqualTo("新增");
            assertThat(wb.getSheet("新增").getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("X");
        }

        // 4. The destination has been re-verified by the read pipeline.
        assertThat(appended.reread().sheets()).hasSize(2);
        assertThat(appended.reread().sheets().get(1).name()).isEqualTo("新增");
    }

    @Test
    void appendSheetLeavesNoTempFiles(@TempDir Path sandbox) throws IOException {
        SpreadsheetCapability capability = capabilityWithOutput(sandbox);

        SpreadsheetResult original = capability.generate(new SpreadsheetRequest(
                "src",
                null,
                List.of(SpreadsheetColumn.of("A")),
                List.of(List.of("1"))));

        capability.appendSheet(original.filePath(), new SpreadsheetAppendRequest(
                "new",
                List.of(SpreadsheetColumn.of("B")),
                List.of(List.of("2"))));

        List<Path> entries;
        try (Stream<Path> stream = Files.list(sandbox)) {
            entries = stream.toList();
        }
        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(p ->
                assertThat(p.getFileName().toString()).doesNotEndWith(".tmp"));
    }

    private SpreadsheetCapability enabledCapability() {
        return capabilityWithOutput(outputDirectory);
    }

    private SpreadsheetCapability capabilityWithOutput(Path outputDir) {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDir);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        return new SpreadsheetCapability(renderer, properties);
    }

    private SpreadsheetCapability smallRowLimitCapability() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDirectory);
        properties.setMaxRows(3);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        return new SpreadsheetCapability(renderer, properties);
    }

    private SpreadsheetCapability disabledCapability() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(false);
        properties.setOutputDirectory(outputDirectory);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        return new SpreadsheetCapability(renderer, properties);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
