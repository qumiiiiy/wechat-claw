package com.weida.bot.wechatilinkbot.infrastructure.pptx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import com.weida.bot.wechatilinkbot.infrastructure.pptx.PptxDocumentClient.PresentationGenerationResult;
import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.TitleSlide;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Atomic-write tests for {@link PptxDocumentClient}. The layout mirrors
 * {@code WordDocumentClientTest}: a real {@link PptxDocumentRenderer}
 * backed by a configured {@link PptxDocumentProperties} plus a manually
 * injected single-thread {@link ExecutorService} so timeout behavior
 * stays deterministic.
 */
class PptxDocumentClientTest {

    @Test
    void writesDeckIntoControlledDirectory(@TempDir Path tempDir) {
        PptxDocumentClient client = enabledClient(tempDir);
        PresentationGenerationResult result = client.generate(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("标题", "副"))));

        assertThat(Files.isRegularFile(result.filePath())).isTrue();
        assertThat(result.filePath().getParent())
                .isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(result.bytes().length).isGreaterThan(0);
        assertThat(result.sha256()).hasSize(32);
        assertThat(result.sha256Hex()).hasSize(64);
    }

    @Test
    void noTempFilesLeftBehindAfterSuccess(@TempDir Path tempDir) throws Exception {
        PptxDocumentClient client = enabledClient(tempDir);
        client.generate(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("标题", "副"))));
        try (var stream = Files.list(tempDir)) {
            assertThat(stream).noneMatch(p -> p.getFileName().toString().contains(".tmp"));
        }
    }

    @Test
    void rejectsCapabilityDisabled(@TempDir Path tempDir) {
        PptxDocumentClient client = disabledClient(tempDir);
        assertThatThrownBy(() -> client.generate(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("标题", "副")))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void outputFileOpensAsValidPptx(@TempDir Path tempDir) throws Exception {
        PptxDocumentClient client = enabledClient(tempDir);
        PresentationGenerationResult result = client.generate(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("标题", "副"))));

        try (InputStream input = Files.newInputStream(result.filePath());
                XMLSlideShow reread = new XMLSlideShow(input)) {
            assertThat(reread.getSlides()).isNotEmpty();
        }
    }

    private static PptxDocumentClient enabledClient(Path outputDir) {
        PptxDocumentProperties properties = new PptxDocumentProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDir);
        PptxDocumentRenderer renderer = new PptxDocumentRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("pptx-test-timeout-"));
        return new PptxDocumentClient(renderer, properties, executor);
    }

    private static PptxDocumentClient disabledClient(Path outputDir) {
        PptxDocumentProperties properties = new PptxDocumentProperties();
        properties.setEnabled(false);
        properties.setOutputDirectory(outputDir);
        PptxDocumentRenderer renderer = new PptxDocumentRenderer(properties);
        ExecutorService executor = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("pptx-test-timeout-"));
        return new PptxDocumentClient(renderer, properties, executor);
    }

    @SuppressWarnings("unused")
    private static byte[] readAll(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @SuppressWarnings("unused")
    private static ByteArrayInputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }
}