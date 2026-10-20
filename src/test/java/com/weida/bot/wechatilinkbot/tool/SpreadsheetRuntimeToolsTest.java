package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import com.weida.bot.wechatilinkbot.tool.input.SpreadsheetAppendToolInput;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetRuntimeToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAndAppendsLatestConversationWorkbookThroughProductionTools() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(tempDir);
        SpreadsheetCapability capability = new SpreadsheetCapability(
                new SpreadsheetRenderer(properties),
                properties);
        try {
            GeneratedMedia source = capability.generateMedia(new SpreadsheetRequest(
                    "课程任务清单",
                    "任务",
                    List.of(SpreadsheetColumn.of("课程"), SpreadsheetColumn.of("任务")),
                    List.of(List.of("算法", "第三章"))));
            byte[] originalBytes = source.bytes();
            ToolContext context = new ToolContext(
                    "conversation",
                    "读取这个 Excel",
                    false,
                    false,
                    false,
                    false,
                    List.of(source));
            ObjectMapper objectMapper = new ObjectMapper();

            ToolExecution read = new SpreadsheetReadTool(capability, objectMapper)
                    .execute(new NoArgumentsToolInput(), context);
            ToolExecution appended = new SpreadsheetAppendTool(capability, objectMapper)
                    .execute(
                            new SpreadsheetAppendToolInput(
                                    "备注",
                                    List.of("内容"),
                                    List.of(List.of("下午演示"))),
                            context);

            assertThat(read.content()).contains("\"status\":\"ok\"", "课程", "任务");
            assertThat(appended.generatedMedia()).isNotNull();
            assertThat(appended.generatedMedia().fileName()).endsWith(".xlsx");
            assertThat(source.bytes()).containsExactly(originalBytes);
            assertThat(capability.read(appended.generatedMedia()).sheets())
                    .extracting(sheet -> sheet.name())
                    .containsExactly("任务", "备注");
        } finally {
            capability.shutdown();
        }
    }

    @Test
    void refusesReadWithoutConversationWorkbook() {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(tempDir);
        SpreadsheetCapability capability = new SpreadsheetCapability(
                new SpreadsheetRenderer(properties),
                properties);
        try {
            ToolExecution result = new SpreadsheetReadTool(capability, new ObjectMapper())
                    .execute(
                            new NoArgumentsToolInput(),
                            new ToolContext("conversation", "读取这个 Excel"));

            assertThat(result.content()).contains("\"status\":\"error\"", "请先生成或选择");
        } finally {
            capability.shutdown();
        }
    }
}
