package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetProperties;
import com.weida.bot.wechatilinkbot.infrastructure.spreadsheet.SpreadsheetRenderer;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.SpreadsheetGenerationTool;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetAgentToolIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void executesModelToolCallAndProducesChannelReadyWorkbook(
            @TempDir Path outputDirectory) throws Exception {
        List<ModelRequest> modelRequests = new ArrayList<>();
        AgentModelClient modelClient = modelClient(request -> {
            modelRequests.add(request);
            return new ModelResponse(
                    "",
                    List.of(new ModelToolCall(
                            "spreadsheet-call",
                            GENERATE_SPREADSHEET,
                            """
                            {
                              "title": "本周作业清单",
                              "sheetName": "本周任务",
                              "columns": ["序号", "课程", "状态"],
                              "rows": [["1", "算法", "进行中"], ["2", "英语", "未开始"]]
                            }
                            """)));
        });
        ToolRegistry registry = registry(outputDirectory);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());
        ToolRunContext runContext = runContext();
        List<ToolDefinition> definitions = registry.listDefinitions(
                true,
                Set.of(GENERATE_SPREADSHEET));

        String result = loop.complete(
                List.of(ModelMessage.user("生成本周作业清单 Excel", List.of())),
                definitions,
                List.of(GENERATE_SPREADSHEET),
                runContext,
                "conversation");

        assertThat(result).contains("\"status\":\"ok\"");
        assertThat(modelRequests).singleElement().satisfies(request -> {
            assertThat(request.toolRequired()).isTrue();
            assertThat(request.tools())
                    .extracting(ToolDefinition::name)
                    .containsExactly(GENERATE_SPREADSHEET);
        });
        assertThat(runContext.generatedMedia()).singleElement().satisfies(media -> {
            assertThat(media.kind()).isEqualTo(GeneratedMediaKind.FILE);
            assertThat(media.fileName()).endsWith(".xlsx");
            Path workbookPath = outputDirectory.resolve(media.fileName());
            try (InputStream inputStream = Files.newInputStream(workbookPath);
                    XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
                assertThat(workbook.getSheetName(0)).isEqualTo("本周任务");
                assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue())
                        .isEqualTo("算法");
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void reportsInvalidWorkbookWithoutProducingMedia(
            @TempDir Path outputDirectory) throws Exception {
        AgentModelClient modelClient = modelClient(request -> new ModelResponse(
                "",
                List.of(new ModelToolCall(
                        "spreadsheet-call",
                        GENERATE_SPREADSHEET,
                        """
                        {
                          "title": "错误清单",
                          "columns": ["课程", "状态"],
                          "rows": [["算法"]]
                        }
                        """))));
        ToolRegistry registry = registry(outputDirectory);
        AgentToolLoop loop = new AgentToolLoop(
                modelClient,
                registry,
                new AgentProperties());
        ToolRunContext runContext = runContext();

        String result = loop.complete(
                List.of(ModelMessage.user("生成 Excel", List.of())),
                registry.listDefinitions(true, Set.of(GENERATE_SPREADSHEET)),
                List.of(GENERATE_SPREADSHEET),
                runContext,
                "conversation");

        assertThat(result)
                .contains("\"status\":\"error\"")
                .contains("spreadsheet_invalid")
                .doesNotContain(outputDirectory.toString());
        assertThat(runContext.generatedMedia()).isEmpty();
    }

    private ToolRegistry registry(Path outputDirectory) {
        SpreadsheetProperties properties = new SpreadsheetProperties();
        properties.setEnabled(true);
        properties.setOutputDirectory(outputDirectory);
        SpreadsheetRenderer renderer = new SpreadsheetRenderer(properties);
        SpreadsheetCapability capability = new SpreadsheetCapability(renderer, properties);
        SpreadsheetGenerationTool tool = new SpreadsheetGenerationTool(
                capability,
                objectMapper);
        return new ToolRegistry(List.of(tool), objectMapper);
    }

    private ToolRunContext runContext() {
        return new ToolRunContext(
                "wechat-ilink|user-1",
                "生成本周作业清单 Excel",
                false,
                false,
                1,
                objectMapper);
    }

    private static AgentModelClient modelClient(Completion completion) {
        return new AgentModelClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ModelResponse complete(ModelRequest request) throws IOException {
                return completion.complete(request);
            }
        };
    }

    @FunctionalInterface
    private interface Completion {

        ModelResponse complete(ModelRequest request) throws IOException;
    }
}
