package com.weida.bot.wechatilinkbot.tool.input;

import com.weida.bot.wechatilinkbot.tool.ToolParameter;
import java.util.List;

/**
 * Strongly typed file search tool input.
 *
 * <p>The model performs the natural-language understanding and produces these structured
 * fields. The server only validates bounds and executes the search.</p>
 *
 * @param nameFragments     file-name keywords (up to 5, any one may match; include synonyms)
 * @param fileTypes         file extensions (up to 10, e.g. pdf, docx)
 * @param directoryHint     directory name or Windows path (e.g. 桌面, D:/campus/self)
 * @param sortCriteria      recent_first, name_asc or size_desc
 * @param directoriesOnly   whether to list directories instead of files
 * @param recentFileReference whether the user refers to a recently received file
 */
public record FileBrowseToolInput(
        @ToolParameter(
                description = "文件名关键词列表（最多5个，可给出同义或相关词，"
                        + "服务端匹配任一即可；不要包含路径分隔符）",
                required = false)
        List<String> nameFragments,
        @ToolParameter(
                description = "文件扩展名列表（最多10个，如 exe、pdf、docx、xlsx）",
                required = false)
        List<String> fileTypes,
        @ToolParameter(
                description = "目录提示：中文目录名（桌面、下载）或 Windows 路径（D:/campus/self）",
                required = false)
        String directoryHint,
        @ToolParameter(
                description = "排序方式：recent_first（最近修改）、name_asc（名称）、"
                        + "size_desc（大小）",
                required = false)
        String sortCriteria,
        @ToolParameter(
                description = "是否只列出目录而不是文件",
                required = false)
        Boolean directoriesOnly,
        @ToolParameter(
                description = "用户是否指代最近收到的文件（“这个文件”“刚才的文件”）；"
                        + "“最近的N个文件”请使用 sortCriteria=recent_first",
                required = false)
        Boolean recentFileReference) {
}
