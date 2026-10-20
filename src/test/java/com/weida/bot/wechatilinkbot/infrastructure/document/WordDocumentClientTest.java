package com.weida.bot.wechatilinkbot.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests covering filesystem boundary, atomic write, verification,
 * read pipeline, and cleanup for {@link WordDocumentClient}.
 */
class WordDocumentClientTest {

    @TempDir
    Path outputDirectory;

    @Test
    void writesAndReopensInsideControlledDirectory() throws IOException {
        WordDocumentClient client = enabledClient();

        WordDocumentClient.DocumentGenerationResult result = client.generate(new DocumentRequest(
                "课程任务清单",
                List.of(
                        new DocumentSection.Heading1Section("任务"),
                        new DocumentSection.ParagraphSection("列出本课需要完成的任务。"),
                        new DocumentSection.TableSection(List.of(
                                List.of("序号", "任务"),
                                List.of("1", "算法作业"),
                                List.of("2", "英语阅读"))))));

        assertThat(result.filePath()).isNotNull();
        assertThat(result.filePath().getParent().toRealPath())
                .isEqualTo(outputDirectory.toRealPath());
        assertThat(result.filePath().getFileName().toString()).endsWith(".docx");
        assertThat(result.verifiedTables()).isEqualTo(1);
        assertThat(result.verifiedSections()).isGreaterThanOrEqualTo(3);
        assertThat(Files.exists(result.filePath())).isTrue();
        assertThat(result.bytes()).isNotEmpty();
        assertThat(result.sha256Hex()).hasSize(64);

        try (InputStream in = Files.newInputStream(result.filePath());
                XWPFDocument reopened = new XWPFDocument(in)) {
            assertThat(reopened.getTables()).hasSize(1);
            assertThat(reopened.getTables().get(0).getRows().get(0).getCell(1).getText())
                    .isEqualTo("任务");
            assertThat(reopened.getTables().get(0).getRow(2).getCell(1).getText())
                    .isEqualTo("英语阅读");
        }
    }

    @Test
    void cannotEscapeOutputDirectoryViaTitle() {
        WordDocumentClient client = enabledClient();

        WordDocumentClient.DocumentGenerationResult result = client.generate(new DocumentRequest(
                "../etc/passwd",
                List.of(new DocumentSection.ParagraphSection("正文"))));

        Path canonical = result.filePath().toAbsolutePath().normalize();
        Path canonicalRoot = outputDirectory.toAbsolutePath().normalize();
        assertThat(canonical.startsWith(canonicalRoot)).isTrue();
        assertThat(result.filePath().getFileName().toString()).doesNotContain("/");
        assertThat(result.filePath().getFileName().toString()).doesNotContain("..");
    }

    @Test
    void rejectsBlankTitle() {
        WordDocumentClient client = enabledClient();

        assertThatThrownBy(() -> client.generate(new DocumentRequest(
                "title",
                java.util.Collections.emptyList())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sections");
    }

    @Test
    void rejectsCapabilityDisabled() {
        WordDocumentClient client = disabledClient();

        assertThatThrownBy(() -> client.generate(new DocumentRequest(
                "off",
                List.of(new DocumentSection.ParagraphSection("x")))))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void noTempFilesLeftBehindAfterSuccessfulGeneration() throws IOException {
        WordDocumentClient client = enabledClient();

        client.generate(new DocumentRequest(
                "清理验证",
                List.of(new DocumentSection.ParagraphSection("a"))));

        List<Path> entries;
        try (Stream<Path> stream = Files.list(outputDirectory)) {
            entries = stream.toList();
        }
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getFileName().toString()).doesNotEndWith(".tmp");
    }

    @Test
    void legacyPlainTextEntryPointStillWorks() throws IOException {
        WordDocumentClient client = enabledClient();

        byte[] bytes = client.generate("legacy title", "line one\nline two");

        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');

        try (XWPFDocument document = new XWPFDocument(
                new java.io.ByteArrayInputStream(bytes))) {
            assertThat(document.getParagraphs().size()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void legacyPlainTextEntryPointRejectsBlankContent() {
        WordDocumentClient client = enabledClient();

        assertThatThrownBy(() -> client.generate("title", "   "))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("内容不能为空");
    }

    @Test
    void writeTimeoutEnforcedAndTempCleanedUp(@TempDir Path sandbox) throws IOException {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(sandbox);
        properties.setWriteTimeout(java.time.Duration.ofMillis(1));
        DocumentRenderer renderer = new DocumentRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        executor.submit(() -> {
            blockerStarted.countDown();
            releaseBlocker.await();
            return null;
        });

        WordDocumentClient client = new WordDocumentClient(renderer, properties, executor);
        try {
            assertThat(blockerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> client.generate(new DocumentRequest(
                    "timeout",
                    List.of(new DocumentSection.ParagraphSection("x")))))
                    .isInstanceOf(DocumentGenerationException.class)
                    .hasMessageContaining("超时");

            try (Stream<Path> stream = Files.list(sandbox)) {
                assertThat(stream).isEmpty();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待测试执行器被中断", exception);
        } finally {
            releaseBlocker.countDown();
            client.shutdown();
        }
    }

    @Test
    void pageBreaksRoundTripThroughAtomicWrite() throws IOException {
        WordDocumentClient client = enabledClient();

        WordDocumentClient.DocumentGenerationResult result = client.generate(new DocumentRequest(
                "分页验证",
                List.of(
                        new DocumentSection.Heading1Section("上半部分"),
                        new DocumentSection.ParagraphSection("上半部分正文"),
                        new DocumentSection.PageBreakSection(),
                        new DocumentSection.Heading2Section("下半部分"),
                        new DocumentSection.ParagraphSection("下半部分正文"))));

        assertThat(result.verifiedPageBreaks()).isEqualTo(1);
        try (InputStream in = Files.newInputStream(result.filePath());
                XWPFDocument reopened = new XWPFDocument(in)) {
            int pageBreaks = 0;
            for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph
                    : reopened.getParagraphs()) {
                for (org.apache.poi.xwpf.usermodel.XWPFRun run : paragraph.getRuns()) {
                    for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr br
                            : run.getCTR().getBrList()) {
                        if (br.getType()
                                == org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE) {
                            pageBreaks++;
                        }
                    }
                }
            }
            assertThat(pageBreaks).isEqualTo(1);
        }
    }

    private WordDocumentClient enabledClient() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDirectory);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        return new WordDocumentClient(renderer, properties);
    }

    private WordDocumentClient disabledClient() {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(false);
        properties.setOutputDirectory(outputDirectory);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        return new WordDocumentClient(renderer, properties);
    }
}