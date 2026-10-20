package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.BROWSE_FILES;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.FileBrowseCapability;
import com.weida.bot.wechatilinkbot.capability.FileBrowseQuery;
import com.weida.bot.wechatilinkbot.capability.FileBrowseProperties;
import com.weida.bot.wechatilinkbot.capability.FileBrowseQueryCompiler;
import com.weida.bot.wechatilinkbot.capability.FileBrowseSearchRequest;
import com.weida.bot.wechatilinkbot.capability.StructuredDirectoryResult;
import com.weida.bot.wechatilinkbot.capability.StructuredFileResult;
import com.weida.bot.wechatilinkbot.tool.input.FileBrowseToolInput;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Lists files and directories under an allowed local path.
 *
 * <p>This tool requires configured allowed paths and provides read-only access to directory
 * structures within those boundaries. Natural-language understanding is done by the model,
 * which passes structured search conditions; this tool only validates and executes them.</p>
 */
@Order(50)
@Component
public class FileBrowseTool implements AgentTool<FileBrowseToolInput> {

    private final FileBrowseCapability fileBrowseCapability;
    private final ObjectMapper objectMapper;
    private final FileBrowseQueryCompiler queryCompiler;

    private static final Logger LOG = LoggerFactory.getLogger(FileBrowseTool.class);

    public FileBrowseTool(
            FileBrowseCapability fileBrowseCapability,
            ObjectMapper objectMapper) {
        this(fileBrowseCapability, new FileBrowseProperties(), objectMapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FileBrowseTool(
            FileBrowseCapability fileBrowseCapability,
            FileBrowseProperties properties,
            ObjectMapper objectMapper) {
        this.fileBrowseCapability = fileBrowseCapability;
        this.objectMapper = objectMapper;
        this.queryCompiler = new FileBrowseQueryCompiler(properties);
    }

    @Override
    public String name() {
        return BROWSE_FILES;
    }

    @Override
    public String description() {
        return "当用户询问本地磁盘文件、目录内容、文件搜索时调用。"
                + "把用户自然语言翻译成结构化参数：nameFragments 提取文件名关键词"
                + "（可给出同义或相关词，例如\u201C课表\u201D和\u201C课程表\u201D），"
                + "fileTypes 提取类型（如 exe、pdf、docx），directoryHint 提取目录，"
                + "sortCriteria 选择排序；用户要求\u201C最近的N个文件\u201D时使用 "
                + "sortCriteria=recent_first 并按修改时间取前 N 个，不要设置 recentFileReference；"
                + "recentFileReference 仅用于用户指代刚收到/刚发送的文件（\u201C这个文件\u201D）；"
                + "要求罗列文件夹时设置 directoriesOnly=true。"
                + "注意：只能搜索配置中允许的目录，不能读取文件内容。";
    }

    @Override
    public Class<FileBrowseToolInput> inputType() {
        return FileBrowseToolInput.class;
    }

    @Override
    public boolean available() {
        return fileBrowseCapability.available();
    }

    @Override
    public String availabilityReason() {
        return fileBrowseCapability.availabilityReason();
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
    public ToolExecution execute(
            FileBrowseToolInput input,
            ToolContext context) {
        if (input == null) {
            throw new IllegalArgumentException("文件搜索条件不能为空");
        }
        LOG.info("browse_files_input nameFragments={} fileTypes={} directoryHint={} sortCriteria={} directoriesOnly={} recentFileReference={}",
                input.nameFragments(), input.fileTypes(), input.directoryHint(),
                input.sortCriteria(), input.directoriesOnly(), input.recentFileReference());
        FileBrowseQuery query = queryCompiler.compile(new FileBrowseSearchRequest(
                input.nameFragments(),
                input.fileTypes(),
                input.directoryHint(),
                input.sortCriteria(),
                input.directoriesOnly(),
                input.recentFileReference()));
        List<StructuredFileResult> results = query.directoriesOnly()
                ? List.of()
                : search(query, input.recentFileReference() == Boolean.TRUE, context);
        List<StructuredDirectoryResult> directories = query.directoriesOnly()
                ? fileBrowseCapability.searchDirectoriesStructured(query, context.conversationId())
                : List.of();
        int resultCount = query.directoriesOnly() ? directories.size() : results.size();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        String queryLabel = !query.nameFragments().isEmpty()
                ? String.join(" ", query.nameFragments())
                : query.directoryHint() != null ? query.directoryHint() : "all";
        result.put("query", queryLabel);
        result.put("entry_type", query.directoriesOnly() ? "directory" : "file");
        result.put("result_count", resultCount);
        result.put("sort_criteria", query.sortCriteria().name());

        if (resultCount == 0) {
            result.put("message", query.directoriesOnly()
                    ? "未找到匹配的文件夹。请尝试修改目录条件。"
                    : "未找到匹配的文件。请尝试修改搜索条件。");
        } else if (resultCount == 1) {
            result.put("message", query.directoriesOnly()
                    ? "找到 1 个文件夹。"
                    : "找到 1 个文件，可直接选择。");
        } else {
            result.put("message", query.directoriesOnly()
                    ? "找到 " + resultCount + " 个文件夹。"
                    : "找到 " + resultCount + " 个文件，请使用序号（如第1个）选择。");
        }

        if (query.directoriesOnly()) {
            ArrayNode directoryArray = result.putArray("directories");
            for (StructuredDirectoryResult directory : directories) {
                ObjectNode directoryNode = directoryArray.addObject();
                directoryNode.put("index", directory.naturalIndex());
                directoryNode.put("directory_name", directory.displayName());
                directoryNode.put("relative_path", directory.relativePath());
            }
        } else {
            ArrayNode resultArray = result.putArray("results");
            for (StructuredFileResult searchResult : results) {
                ObjectNode resultNode = resultArray.addObject();
                resultNode.put("index", searchResult.naturalIndex());
                resultNode.put("file_name", searchResult.displayName());
                resultNode.put("directory", searchResult.relativeDirectory());
                resultNode.put("size_bytes", searchResult.sizeBytes());
                resultNode.put("size_human", searchResult.humanReadableSize());
                resultNode.put("last_modified", searchResult.lastModifiedTime().toString());
                resultNode.put("file_id", searchResult.fileId());
            }
        }

        return new ToolExecution(result.toString(), null);
    }

    private List<StructuredFileResult> search(
            FileBrowseQuery query,
            boolean recentFileReference,
            ToolContext context) {
        boolean pureRecentReference = recentFileReference
                && !context.recentInboundFileNames().isEmpty()
                && !query.hasNameFragments()
                && !query.hasFileTypes()
                && !query.hasDirectoryHint();
        if (!pureRecentReference) {
            return fileBrowseCapability.searchStructured(query, context.conversationId());
        }
        for (String fileName : context.recentInboundFileNames()) {
            FileBrowseQuery fileQuery = new FileBrowseQuery(
                    List.of(fileName),
                    query.fileTypes(),
                    query.directoryHint(),
                    query.modifiedAfter(),
                    query.sortCriteria());
            List<StructuredFileResult> results =
                    fileBrowseCapability.searchStructured(fileQuery, context.conversationId());
            if (!results.isEmpty()) {
                return results;
            }
        }
        return List.of();
    }
}
