package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.PresentationCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.PresentationGenerationToolInput;
import org.junit.jupiter.api.Test;

class PresentationGenerationToolTest {

    @Test
    void convertsBoundedOutlineIntoGeneratedFileResult() {
        PresentationCapability capability = mock(PresentationCapability.class);
        when(capability.available()).thenReturn(true);
        when(capability.generate(any())).thenReturn(new GeneratedMedia(
                GeneratedMediaKind.FILE,
                new byte[] {1, 2, 3},
                "项目复盘.pptx",
                0L,
                ""));
        PresentationGenerationTool tool = new PresentationGenerationTool(
                capability,
                new ObjectMapper());

        ToolExecution execution = tool.execute(
                new PresentationGenerationToolInput(
                        "项目复盘",
                        "[{\"type\":\"title\",\"title\":\"项目复盘\"},"
                                + "{\"type\":\"bullets\",\"heading\":\"结论\","
                                + "\"bullets\":[\"按期交付\"]}]"),
                context());

        assertThat(execution.content()).contains("\"status\":\"ok\"", "项目复盘.pptx");
        assertThat(execution.generatedMedia()).isNotNull();
    }

    @Test
    void rejectsModelSuppliedPicturePath() {
        PresentationGenerationTool tool = new PresentationGenerationTool(
                mock(PresentationCapability.class),
                new ObjectMapper());

        ToolExecution execution = tool.execute(
                new PresentationGenerationToolInput(
                        "不安全的演示",
                        "[{\"type\":\"picture\",\"heading\":\"图片\","
                                + "\"picturePath\":\"C:/private/secret.png\"}]"),
                context());

        assertThat(execution.content())
                .contains("\"status\":\"error\"")
                .contains("不能直接提供图片路径");
    }

    private static ToolContext context() {
        return new ToolContext(
                "wechat|a",
                "生成 PPTX",
                false,
                false,
                false,
                false,
                java.util.List.of());
    }
}
