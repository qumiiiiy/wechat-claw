package com.weida.bot.wechatilinkbot.infrastructure.pptx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import com.weida.bot.wechatilinkbot.model.PresentationSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.BulletSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.EndingSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.PictureSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.TableSlide;
import com.weida.bot.wechatilinkbot.model.PresentationSlide.TitleSlide;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link PptxDocumentRenderer}.
 */
class PptxDocumentRendererTest {

    @Test
    void rendersAllFiveLayoutsInOrder() throws Exception {
        PptxDocumentRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new PresentationRequest(
                "演示",
                List.of(
                        new TitleSlide("标题", "副标题"),
                        new BulletSlide("要点", List.of("要点1", "要点2", "要点3")),
                        new PictureSlide("图片", "说明", "missing.png"),
                        new TableSlide("表格", List.of(
                                List.of("A", "1"),
                                List.of("B", "2"))),
                        new EndingSlide("谢谢观看"))));

        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            assertThat(slideShow.getSlides()).hasSize(5);
            // Each slide renders its title/heading via a text box; assert on
            // the joined text of every XSLFTextBox.
            assertThat(joinedText(slideShow.getSlides().get(0))).contains("标题").contains("副标题");
            assertThat(joinedText(slideShow.getSlides().get(1))).contains("要点").contains("要点1");
            assertThat(joinedText(slideShow.getSlides().get(2))).contains("图片");
            // Table slide: heading is on a text box; cell content lives on XSLFTable.
            assertThat(joinedText(slideShow.getSlides().get(3))).contains("表格");
            XSLFSlide tableSlide = slideShow.getSlides().get(3);
            XSLFTable table = (XSLFTable) tableSlide.getShapes().stream()
                    .filter(s -> s instanceof XSLFTable)
                    .findFirst()
                    .orElseThrow();
            assertThat(table.getCell(0, 0).getText()).contains("A");
            assertThat(table.getCell(1, 1).getText()).contains("2");
            assertThat(joinedText(slideShow.getSlides().get(4))).contains("谢谢观看");
        }
    }

    private static String joinedText(XSLFSlide slide) {
        return slide.getShapes().stream()
                .filter(s -> s instanceof org.apache.poi.xslf.usermodel.XSLFTextBox)
                .map(s -> ((org.apache.poi.xslf.usermodel.XSLFTextBox) s).getText())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    @Test
    void rejectsCapabilityDisabled() {
        PptxDocumentRenderer renderer = disabledRenderer();
        assertThatThrownBy(() -> renderer.render(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("标题", "副")))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void rejectsTitleStartingWithEqualsSign() {
        PptxDocumentRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new PresentationRequest(
                "演示",
                List.of(new TitleSlide("=evil", "")))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsBulletStartingWithPlusSign() {
        PptxDocumentRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new PresentationRequest(
                "演示",
                List.of(new BulletSlide("要点", List.of("+1+1"))))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("公式注入前缀");
    }

    @Test
    void rejectsSlideCountOverLimit() {
        PptxDocumentProperties props = new PptxDocumentProperties();
        props.setEnabled(true);
        props.setMaxSlides(2);
        PptxDocumentRenderer renderer = new PptxDocumentRenderer(props);

        assertThatThrownBy(() -> renderer.render(new PresentationRequest(
                "演示",
                List.of(
                        new TitleSlide("T1", "S1"),
                        new EndingSlide("E1"),
                        new EndingSlide("E2")))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("slide 数量");
    }

    @Test
    void rejectsTableColumnCountMismatch() {
        PptxDocumentRenderer renderer = enabledRenderer();
        assertThatThrownBy(() -> renderer.render(new PresentationRequest(
                "演示",
                List.of(new TableSlide("Tab", List.of(
                        List.of("A", "1"),
                        List.of("B")))))))
                .isInstanceOf(PptxGenerationException.class)
                .hasMessageContaining("列数");
    }

    @Test
    void rendersBulletSlideWithBulletMarkers() throws Exception {
        PptxDocumentRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new PresentationRequest(
                "演示",
                List.of(new BulletSlide("要点", List.of("X", "Y")))));
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            XSLFSlide slide = slideShow.getSlides().get(0);
            String joined = slide.getShapes().stream()
                    .filter(s -> s instanceof org.apache.poi.xslf.usermodel.XSLFTextBox)
                    .map(s -> ((org.apache.poi.xslf.usermodel.XSLFTextBox) s).getText())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            assertThat(joined).contains("• X");
            assertThat(joined).contains("• Y");
        }
    }

    @Test
    void rendersTableSlideWithRows() throws Exception {
        PptxDocumentRenderer renderer = enabledRenderer();
        byte[] bytes = renderer.render(new PresentationRequest(
                "演示",
                List.of(new TableSlide("Tab", List.of(
                        List.of("A", "1"),
                        List.of("B", "2"))))));
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            XSLFSlide slide = slideShow.getSlides().get(0);
            assertThat(slide.getShapes().stream()
                    .anyMatch(s -> s instanceof org.apache.poi.xslf.usermodel.XSLFTable))
                    .isTrue();
        }
    }

    private static PptxDocumentRenderer enabledRenderer() {
        PptxDocumentProperties props = new PptxDocumentProperties();
        props.setEnabled(true);
        return new PptxDocumentRenderer(props);
    }

    private static PptxDocumentRenderer disabledRenderer() {
        PptxDocumentProperties props = new PptxDocumentProperties();
        props.setEnabled(false);
        return new PptxDocumentRenderer(props);
    }
}