package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.SpreadsheetGenerationToolInput;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the XLSX Tool correctly translates a model-supplied input
 * into the capability call and produces a verifiable on-disk workbook.
 */
class SpreadsheetGenerationToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toolNameAndAvailabilitySurfaceAreStable(@TempDir Path outputDir) {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        assertThat(tool.name()).isEqualTo("generate_spreadsheet");
        assertThat(tool.inputType())
                .isEqualTo(SpreadsheetGenerationToolInput.class);
        assertThat(tool.available()).isTrue();
        assertThat(tool.requiresExplicitIntent()).isTrue();
        assertThat(tool.producesMedia()).isTrue();
        assertThat(tool.returnDirect()).isTrue();
    }

    @Test
    void executesAndReturnsChannelReadyMedia(@TempDir Path outputDir) throws Exception {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        ToolExecution execution = tool.execute(
                new SpreadsheetGenerationToolInput(
                        "本周课程清单",
                        "本周任务",
                        List.of("序号", "课程", "状态"),
                        List.of(
                                List.of("1", "算法", "进行中"),
                                List.of("2", "英语", "未开始"))),
                new ToolContext("conversation-1", "生成课程清单"));

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.generatedMedia().kind())
                .isEqualTo(GeneratedMediaKind.FILE);
        assertThat(execution.generatedMedia().fileName()).endsWith(".xlsx");

        JsonNode result = objectMapper.readTree(execution.content());
        assertThat(result.get("status").asText()).isEqualTo("ok");
        assertThat(result.get("media_type").asText()).isEqualTo("file");
        assertThat(result.get("bytes").asLong()).isGreaterThan(0);

        // Independent reopen to confirm the tool's output is a valid workbook.
        Path written = outputDir.resolve(execution.generatedMedia().fileName());
        try (InputStream in = Files.newInputStream(written);
                XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetName(0)).isEqualTo("本周任务");
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("序号");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("算法");
        }
    }

    @Test
    void handlesEmptyRowsByProducingHeaderOnlyWorkbook(@TempDir Path outputDir) {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        ToolExecution execution = tool.execute(
                new SpreadsheetGenerationToolInput(
                        "仅表头",
                        null,
                        List.of("A", "B"),
                        null),
                new ToolContext("conversation-2", "只生成表头"));

        assertThat(execution.generatedMedia()).isNotNull();
        assertThat(execution.content()).contains("\"status\":\"ok\"");
    }

    @Test
    void rejectsBlankTitleAsInvalidArgument(@TempDir Path outputDir) {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        ToolExecution execution = tool.execute(
                new SpreadsheetGenerationToolInput(
                        "",
                        null,
                        List.of("A"),
                        null),
                new ToolContext("conversation-3", "空标题"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content()).contains("\"status\":\"error\"");
        assertThat(execution.content()).contains("invalid_argument");
    }

    @Test
    void rejectsDuplicateColumnsAsSpreadsheetInvalid(@TempDir Path outputDir) {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        ToolExecution execution = tool.execute(
                new SpreadsheetGenerationToolInput(
                        "重复列",
                        null,
                        List.of("Id", "ID"),
                        List.of(List.of("1", "2"))),
                new ToolContext("conversation-4", "重复列"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content()).contains("\"status\":\"error\"");
        assertThat(execution.content()).contains("spreadsheet_invalid");
        assertThat(execution.content())
                .contains("工作簿参数无效或生成失败")
                .doesNotContain(outputDir.toString());
    }

    @Test
    void rejectsRowColumnArityMismatch(@TempDir Path outputDir) {
        SpreadsheetGenerationTool tool = toolWith(outputDir);

        ToolExecution execution = tool.execute(
                new SpreadsheetGenerationToolInput(
                        "列数不一致",
                        null,
                        List.of("A", "B"),
                        List.of(List.of("only-one"))),
                new ToolContext("conversation-5", "列数不一致"));

        assertThat(execution.generatedMedia()).isNull();
        assertThat(execution.content()).contains("spreadsheet_invalid");
    }

    @Test
    void reportsUnavailableWhenCapabilityDisabled(@TempDir Path outputDir) {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(false);
        properties.setOutputDirectory(outputDir);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        SpreadsheetCapability capability = new SpreadsheetCapability(renderer, properties);
        SpreadsheetGenerationTool tool = new SpreadsheetGenerationTool(
                capability, objectMapper);

        assertThat(tool.available()).isFalse();
    }

    private SpreadsheetGenerationTool toolWith(Path outputDir) {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDir);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        SpreadsheetCapability capability = new SpreadsheetCapability(renderer, properties);
        return new SpreadsheetGenerationTool(capability, objectMapper);
    }
}
