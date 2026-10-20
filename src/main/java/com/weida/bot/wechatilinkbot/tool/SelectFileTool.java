package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.SELECT_FILE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.FileBrowseCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.FileSelectionToolInput;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Selects a previously searched file after revalidating its opaque ID and identity. */
@Order(51)
@Component
public class SelectFileTool implements AgentTool<FileSelectionToolInput> {

    private static final Pattern NATURAL_INDEX_PATTERN = Pattern.compile(
            "(?:第|选择第?)?(\\d+)(?:个|号)?",
            Pattern.CASE_INSENSITIVE);

    private final FileBrowseCapability capability;
    private final ObjectMapper objectMapper;

    public SelectFileTool(FileBrowseCapability capability, ObjectMapper objectMapper) {
        this.capability = capability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return SELECT_FILE;
    }

    @Override
    public String description() {
        return "选择文件搜索结果中的文件。"
                + "支持两种方式：1) 提供完整 file ID；2) 提供自然序号（如第1个、选择2号）。"
                + "服务端会重新验证有效期、允许目录、文件类型、大小和文件身份。";
    }

    @Override
    public Class<FileSelectionToolInput> inputType() {
        return FileSelectionToolInput.class;
    }

    @Override
    public boolean available() {
        return capability.available();
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
    public ToolExecution execute(FileSelectionToolInput input, ToolContext context) {
        GeneratedMedia media;

        // Try to parse as natural index first
        Integer naturalIndex = parseNaturalIndex(input.fileId());
        if (naturalIndex != null) {
            media = capability.selectByNaturalIndex(naturalIndex, context.conversationId());
        } else {
            // Fall back to file ID
            media = capability.select(input.fileId(), context.conversationId());
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        result.put("media_type", "file");
        result.put("file_name", media.fileName());
        result.put("bytes", media.bytes().length);
        result.put("message", "文件已安全选择，将作为当前微信消息的回复发送");
        return new ToolExecution(result.toString(), media);
    }

    private Integer parseNaturalIndex(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher matcher = NATURAL_INDEX_PATTERN.matcher(input.strip());
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
