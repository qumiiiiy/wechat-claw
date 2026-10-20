package com.weida.bot.wechatilinkbot.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest;
import com.weida.bot.wechatilinkbot.model.DocumentEditRequest.TextReplacement;
import com.weida.bot.wechatilinkbot.model.DocumentEditResult;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Atomic-write tests for {@link WordDocumentClient#edit(Path, DocumentEditRequest)}.
 *
 * <p>The test layout mirrors {@link WordDocumentClientTest}: a real
 * {@link DocumentRenderer} backed by a {@link DocumentGenerationProperties}
 * instance configured with the test temp directory, plus a manually
 * injected single-thread {@link ExecutorService} so timeout enforcement can
 * be tested deterministically.</p>
 */
class WordDocumentClientEditTest {

    @Test
    void writesEditedCopyIntoControlledDirectory(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始标题", List.of(
                new DocumentSection.Heading1Section("章节一"),
                new DocumentSection.ParagraphSection("Hello World")));
        byte[] sourceDigest = sha256(Files.readAllBytes(source));

        DocumentEditResult result = client.edit(source,
                new DocumentEditRequest(
                        source,
                        "新标题",
                        List.of(new TextReplacement("Hello", "你好", true))));

        assertThat(Files.isRegularFile(result.filePath())).isTrue();
        assertThat(result.filePath().getParent()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(result.replacementsApplied()).isPositive();
        assertThat(result.bytes().length).isGreaterThan(0);

        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(result.bytes()))) {
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("新标题");
            assertThat(document.getParagraphs().get(2).getText()).contains("你好 World");
        }

        assertThat(sha256(Files.readAllBytes(source))).isEqualTo(sourceDigest);
        assertThat(result.sourceSha256()).isEqualTo(sourceDigest);
        assertThat(result.sourceSha256Hex()).hasSize(64);
    }

    @Test
    void preservesSourceFileBytes(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始标题", List.of(
                new DocumentSection.ParagraphSection("Hello World")));
        byte[] before = Files.readAllBytes(source);

        client.edit(source,
                new DocumentEditRequest(
                        source,
                        "新标题",
                        List.of(new TextReplacement("Hello", "你好", true))));

        byte[] after = Files.readAllBytes(source);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void noTempFilesLeftBehindAfterSuccessfulEdit(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始", List.of(
                new DocumentSection.ParagraphSection("Hello World")));

        client.edit(source,
                new DocumentEditRequest(
                        source,
                        "新标题",
                        List.of(new TextReplacement("Hello", "你好", true))));

        try (var stream = Files.list(tempDir)) {
            assertThat(stream).noneMatch(p -> p.getFileName().toString().contains(".tmp"));
        }
    }

    @Test
    void rejectsWhenSourceNotRegularFile(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path missing = tempDir.resolve("not-here.docx");

        assertThatThrownBy(() -> client.edit(missing,
                new DocumentEditRequest(missing, "新标题", List.of())))
                .isInstanceOf(DocumentReadException.class)
                .hasMessageContaining("源文件");
    }

    @Test
    void rejectsRequestWithNeitherTitleNorReplacements(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始", List.of(
                new DocumentSection.ParagraphSection("Hello")));

        assertThatThrownBy(() -> client.edit(source,
                new DocumentEditRequest(source, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("newTitle");
    }

    @Test
    void rejectsWhenCapabilityDisabled(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = disabledClient(tempDir);
        Path source = writeSource(enabledClient(tempDir), tempDir, "原始", List.of(
                new DocumentSection.ParagraphSection("Hello")));

        assertThatThrownBy(() -> client.edit(source,
                new DocumentEditRequest(source, "新标题", List.of())))
                .isInstanceOf(DocumentGenerationException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void outputFileOpensAsValidDocx(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始", List.of(
                new DocumentSection.ParagraphSection("Hello World")));

        DocumentEditResult result = client.edit(source,
                new DocumentEditRequest(
                        source,
                        null,
                        List.of(new TextReplacement("Hello", "你好", true))));

        try (InputStream input = Files.newInputStream(result.filePath());
                XWPFDocument reread = new XWPFDocument(input)) {
            assertThat(reread.getParagraphs()).isNotEmpty();
        }
    }

    @Test
    void replacementOnlyDoesNotCreateEmptyOutput(@TempDir Path tempDir) throws Exception {
        WordDocumentClient client = enabledClient(tempDir);
        Path source = writeSource(client, tempDir, "原始", List.of(
                new DocumentSection.ParagraphSection("abc")));

        DocumentEditResult result = client.edit(source,
                new DocumentEditRequest(
                        source,
                        null,
                        List.of(new TextReplacement("zzz", "yyy", true))));

        assertThat(result.replacementsApplied()).isZero();
        assertThat(result.bytes().length).isGreaterThan(0);
    }

    private static Path writeSource(
            WordDocumentClient client,
            Path outputDir,
            String title,
            List<DocumentSection> sections) throws IOException {
        byte[] sourceBytes = client.generate(new DocumentRequest(title, sections)).bytes();
        Path source = outputDir.resolve("source.docx");
        Files.write(source, sourceBytes);
        return source;
    }

    private static WordDocumentClient enabledClient(Path outputDir) {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDir);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("document-edit-test-timeout-"));
        return new WordDocumentClient(renderer, properties, executor);
    }

    private static WordDocumentClient disabledClient(Path outputDir) {
        DocumentGenerationProperties properties = new DocumentGenerationProperties();
        properties.setEnabled(false);
        properties.setOutputDirectory(outputDir);
        DocumentRenderer renderer = new DocumentRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("document-edit-test-timeout-"));
        return new WordDocumentClient(renderer, properties, executor);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}