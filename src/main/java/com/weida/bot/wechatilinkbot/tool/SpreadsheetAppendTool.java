package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.APPEND_SPREADSHEET;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.SpreadsheetCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.SpreadsheetAppendRequest;
import com.weida.bot.wechatilinkbot.model.SpreadsheetColumn;
import com.weida.bot.wechatilinkbot.tool.input.SpreadsheetAppendToolInput;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds one structured sheet to a copy of the latest trusted XLSX file. */
@Order(53)
@Component
public class SpreadsheetAppendTool implements AgentTool<SpreadsheetAppendToolInput> {

    private final SpreadsheetCapability capability;
    private final ObjectMapper objectMapper;

    public SpreadsheetAppendTool(SpreadsheetCapability capability, ObjectMapper objectMapper) {
        this.capability = capability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return APPEND_SPREADSHEET;
    }

    @Override
    public String description() {
        return "仅当用户明确要求给当前会话最近的 XLSX 文件增加工作表时调用。"
                + "原文件不修改，结果保存为受控副本并作为微信文件回复；不接受路径。";
    }

    @Override
    public Class<SpreadsheetAppendToolInput> inputType() {
        return SpreadsheetAppendToolInput.class;
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
        return true;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(SpreadsheetAppendToolInput input, ToolContext context) {
        var source = SpreadsheetReadTool.latestXlsx(context);
        if (source.isEmpty()) {
            return new ToolExecution(
                    "{\"status\":\"error\",\"message\":\"请先生成或选择一个 .xlsx 文件\"}",
                    null);
        }
        if (input.columns() == null || input.columns().isEmpty()) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        List<SpreadsheetColumn> columns = input.columns().stream()
                .map(SpreadsheetColumn::of)
                .toList();
        SpreadsheetAppendRequest request = new SpreadsheetAppendRequest(
                input.sheetName(),
                columns,
                input.rows());
        GeneratedMedia media = capability.appendSheet(source.get(), request);
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "ok");
        response.put("file_name", media.fileName());
        response.put("bytes", media.bytes().length);
        response.put("message", "已生成包含新工作表的副本，将作为微信文件发送");
        return new ToolExecution(response.toString(), media);
    }
}
