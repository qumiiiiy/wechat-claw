package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;
import java.util.List;

/**
 * Strongly typed input for the XLSX generation tool.
 *
 * <p>The model supplies raw column headers and a 2-D cell grid. The tool
 * converts these into the {@code model} layer's {@code SpreadsheetRequest}
 * representation, and the capability layer is responsible for the server
 * filesystem path. Callers MUST NOT supply file names or output paths —
 * there is no parameter for them.</p>
 *
 * @param title     document title; also used to derive the server-side file
 *                  basename
 * @param sheetName optional sheet name; falls back to {@code "Sheet1"}
 *                  when blank
 * @param columns   ordered column headers (non-empty, deduplicated)
 * @param rows      data rows; each row must have the same arity as
 *                  {@code columns}
 */
public record SpreadsheetGenerationToolInput(
        @ToolParameter(
                description = "工作簿标题，必须非空。模型不要提供文件路径或文件名。"
                        + "例如：'本周课程任务清单'") String title,
        @ToolParameter(
                required = false,
                description = "可选的工作表名。Excel 单表名最长 31 字符，"
                        + "省略时使用 'Sheet1'") String sheetName,
        @ToolParameter(
                description = "列定义列表，每个元素是一个列标题字符串。"
                        + "例如：['序号','课程','任务','截止']。"
                        + "不能为空，不能重复（按大小写不敏感比较），"
                        + "也不能出现空白") List<String> columns,
        @ToolParameter(
                description = "数据行列表，每个元素是一个 list，"
                        + "对应一行的单元格文本（按列顺序）。"
                        + "每行单元格数必须与 columns 数量一致。"
                        + "例如：[['1','算法','完成第三章练习','2026-08-02']]。"
                        + "省略时只生成表头（无数据行）") List<List<String>> rows) {
}