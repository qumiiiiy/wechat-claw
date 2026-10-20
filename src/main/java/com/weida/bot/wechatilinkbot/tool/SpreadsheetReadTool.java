package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.READ_SPREADSHEET;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.SpreadsheetReadResult;
import com.weida.bot.wechatilinkbot.model.SpreadsheetSheetView;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Reads the latest server-owned XLSX file retained for this conversation. */
@Order(52)
@Component
public class SpreadsheetReadTool implements AgentTool<NoArgumentsToolInput> {

    private final SpreadsheetCapability capability;
    private final ObjectMapper objectMapper;

    public SpreadsheetReadTool(SpreadsheetCapability capability, ObjectMapper objectMapper) {
        this.capability = capability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return READ_SPREADSHEET;
    }

    @Override
    public String description() {
        return "仅当用户明确要求读取当前会话最近生成或安全选择的 XLSX 文件时调用；不接受路径。";
    }

    @Override
    public Class<NoArgumentsToolInput> inputType() {
        return NoArgumentsToolInput.class;
    }

    @Override
    public boolean available() {
        return capability.isAvailable();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return false;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(NoArgumentsToolInput input, ToolContext context) {
        Optional<GeneratedMedia> source = latestXlsx(context);
        if (source.isEmpty()) {
            return new ToolExecution(
                    "{\"status\":\"error\",\"message\":\"请先生成或选择一个 .xlsx 文件\"}",
                    null);
        }
        SpreadsheetReadResult result = capability.read(source.get());
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "ok");
        response.put("file_name", source.get().fileName());
        response.put("total_bytes", result.totalBytes());
        ArrayNode sheets = response.putArray("sheets");
        for (SpreadsheetSheetView sheet : result.sheets()) {
            ObjectNode view = sheets.addObject();
            view.put("name", sheet.name());
            view.put("row_count", sheet.rowCount());
            view.putPOJO("headers", sheet.headers());
        }
        return new ToolExecution(response.toString(), null);
    }

    static Optional<GeneratedMedia> latestXlsx(ToolContext context) {
        for (int index = context.availableGeneratedFiles().size() - 1; index >= 0; index--) {
            GeneratedMedia media = context.availableGeneratedFiles().get(index);
            if (media.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
                return Optional.of(media);
            }
        }
        return Optional.empty();
    }
}
