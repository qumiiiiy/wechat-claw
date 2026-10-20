package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.capability.TextFileCapability;
import com.weida.bot.wechatilinkbot.capability.TextFileProperties;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.TextFileGenerationToolInput;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TextFileGenerationToolTest {

    private TextFileProperties properties;
    private TextFileGenerationTool tool;

    @BeforeEach
    void setUp() {
        properties = new TextFileProperties();
        properties.setEnabled(true);
        tool = new TextFileGenerationTool(
                new TextFileCapability(properties),
                new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void createsBoundedUtf8FileInMemory() {
        TextFileGenerationToolInput input = new TextFileGenerationToolInput(
                "使用说明.md",
                "# 使用说明\n\n安全生成。\n");

        ToolExecution execution = tool.execute(input, context());

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().kind()).isEqualTo(GeneratedMediaKind.FILE);
        assertThat(execution.generatedMedia().fileName()).isEqualTo("使用说明.md");
        assertThat(new String(execution.generatedMedia().bytes(), StandardCharsets.UTF_8))
                .isEqualTo("# 使用说明\n\n安全生成。\n");
    }

    @Test
    void rejectsPathTraversalAndBinaryExtension() {
        TextFileGenerationToolInput traversalInput =
                new TextFileGenerationToolInput("../secret.txt", "secret");
        TextFileGenerationToolInput binaryInput =
                new TextFileGenerationToolInput("payload.exe", "payload");

        assertThatThrownBy(() -> tool.execute(traversalInput, context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件名不安全");
        assertThatThrownBy(() -> tool.execute(binaryInput, context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件扩展名");
    }

    @Test
    void enforcesUtf8ByteLimitAfterCharacterValidation() {
        properties.setMaxContentCharacters(10);
        properties.setMaxContentBytes(4);
        TextFileGenerationToolInput input =
                new TextFileGenerationToolInput("answer.txt", "中文");

        assertThatThrownBy(() -> tool.execute(input, context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("字节数限制");
    }

    private static ToolContext context() {
        return new ToolContext("conversation-1", "生成文件");
    }
}
