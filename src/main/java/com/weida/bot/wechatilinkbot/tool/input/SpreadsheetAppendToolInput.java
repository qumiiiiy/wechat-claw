package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;
import java.util.List;

/** Structured data for appending a sheet to the latest trusted XLSX file. */
public record SpreadsheetAppendToolInput(
        @ToolParameter(description = "要新增的工作表名，最长 31 个字符") String sheetName,
        @ToolParameter(description = "新工作表的列标题，不能为空或重复") List<String> columns,
        @ToolParameter(
                required = false,
                description = "新工作表的数据行，每行单元格数量必须等于列数")
        List<List<String>> rows) {
}
