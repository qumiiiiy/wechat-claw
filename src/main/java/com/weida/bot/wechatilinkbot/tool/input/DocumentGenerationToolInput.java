package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;

/**
 * Strongly typed document generation tool input.
 *
 * <p>The plain-text {@code content} path remains supported for backward
 * compatibility. Structured DOCX generation is driven through
 * {@code sectionsJson}: when provided, the renderer uses
 * {@link com.weida.bot.wechatilinkbot.model.DocumentSection} types and
 * ignores {@code content}. The two paths are mutually exclusive; passing
 * both raises a tool-side error.</p>
 *
 * @param fileName     target file name with extension (.docx or .pdf)
 * @param title        optional document title
 * @param content      plain-text body used when {@code sectionsJson} is absent;
 *                     use {@code \\n} for line breaks
 * @param sectionsJson optional JSON array describing ordered
 *                     {@link com.weida.bot.wechatilinkbot.model.DocumentSection}
 *                     entries; mutually exclusive with {@code content}
 *
 * @author ryx
 * @since 2026-07-24
 */
public record DocumentGenerationToolInput(
        @ToolParameter(description = "目标文件名，必须包含扩展名 .docx 或 .pdf，如：报告.docx 或 会议纪要.pdf") String fileName,
        @ToolParameter(required = false, description = "文档标题，将显示在文档开头") String title,
        @ToolParameter(required = false, description = "文档正文内容，使用 \\n 表示换行；与 sectionsJson 互斥") String content,
        @ToolParameter(required = false, description = "结构化 sections 的 JSON 数组，支持 heading/paragraph/bullet_list/ordered_list/table/page_break；与 content 互斥") String sectionsJson) {
}