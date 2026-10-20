package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.model.SpreadsheetRequest;
import com.weida.bot.wechatilinkbot.tool.input.SpreadsheetGenerationToolInput;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent-callable tool that produces a single XLSX workbook for channel
 * delivery.
 *
 * <p>The capability owns the output filesystem and the file basename; this
 * tool is intentionally NOT a channel tool (it does not call
 * {@code Channel.sendFile} directly) — the framework dispatches
 * {@link GeneratedMedia} produced by {@code producesMedia()} tools through
 * the channel adapter after the tool returns.</p>
 *
 * <p>The model MUST NOT supply a file name or output path; the
 * {@link SpreadsheetGenerationToolInput} record has no such fields.</p>
 */
@Order(50)
@Component
public class SpreadsheetGenerationTool
        implements AgentTool<SpreadsheetGenerationToolInput> {

    private static final String INVALID_WORKBOOK_MESSAGE =
            "工作簿参数无效或生成失败，请检查标题、列和数据行";

    private final SpreadsheetCapability spreadsheetCapability;
    private final ObjectMapper objectMapper;

    public SpreadsheetGenerationTool(
            SpreadsheetCapability spreadsheetCapability,
            ObjectMapper objectMapper) {
        this.spreadsheetCapability = spreadsheetCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_SPREADSHEET;
    }

    @Override
    public String description() {
        return "当用户明确要求生成 Excel 工作簿（.xlsx）时调用。"
                + "输入：标题、可选工作表名、列定义、数据行。"
                + "模型不能提供文件路径或文件名，输出路径由服务端按配置决定。"
                + "每行单元格数必须与列定义数量一致；列名不能为空或重复。"
                + "省略数据行时只生成表头。生成后将作为微信文件发送给用户。";
    }

    @Override
    public Class<SpreadsheetGenerationToolInput> inputType() {
        return SpreadsheetGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return spreadsheetCapability.isAvailable();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return true;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(
            SpreadsheetGenerationToolInput input,
            ToolContext context) {
        try {
            List<SpreadsheetColumn> columns = toColumns(input.columns());
            List<List<String>> rows = normalizeRows(input.rows());
            SpreadsheetRequest request = new SpreadsheetRequest(
                    input.title(),
                    input.sheetName(),
                    columns,
                    rows);

            GeneratedMedia media = spreadsheetCapability.generateMedia(request);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "ok");
            result.put("media_type", "file");
            result.put("file_name", media.fileName());
            result.put("bytes", media.bytes().length);
            result.put("message", "Excel 工作簿已生成，将作为微信文件发送");
            return new ToolExecution(result.toString(), media);
        } catch (IllegalArgumentException exception) {
            return errorResult("invalid_argument", exception.getMessage());
        } catch (RuntimeException exception) {
            String type = exception.getClass().getSimpleName();
            String code = "SpreadsheetWriteException".equals(type)
                    ? "spreadsheet_invalid"
                    : "spreadsheet_failed";
            return errorResult(code, INVALID_WORKBOOK_MESSAGE);
        }
    }

    private List<SpreadsheetColumn> toColumns(List<String> rawColumns) {
        List<SpreadsheetColumn> columns = new ArrayList<>();
        for (String raw : rawColumns) {
            columns.add(new SpreadsheetColumn(raw, null));
        }
        return columns;
    }

    private List<List<String>> normalizeRows(List<List<String>> rawRows) {
        if (rawRows == null) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>(rawRows.size());
        for (List<String> row : rawRows) {
            rows.add(row == null ? List.of() : List.copyOf(row));
        }
        return rows;
    }

    private ToolExecution errorResult(String errorType, String message) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "error");
        result.put("error_type", errorType);
        result.put("message", message == null ? "未知错误" : message);
        return new ToolExecution(result.toString(), null);
    }
}
