package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DOCUMENT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.DocumentGenerationCapability;
import com.weida.bot.wechatilinkbot.model.DocumentRequest;
import com.weida.bot.wechatilinkbot.model.DocumentSection;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.DocumentGenerationToolInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that generates Word (.docx) and PDF (.pdf) documents.
 *
 * <p>The plain-text path is kept for backward compatibility. Structured
 * DOCX generation goes through {@code sectionsJson}, parsed into
 * {@link DocumentSection} instances, validated, and rendered with the
 * {@link DocumentRequest} entry point.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Order(45)
@Component
public class DocumentGenerationTool implements AgentTool<DocumentGenerationToolInput> {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentGenerationTool.class);

    private static final TypeReference<List<JsonNode>> SECTION_LIST_TYPE =
            new TypeReference<>() { };

    private final DocumentGenerationCapability documentCapability;
    private final ObjectMapper objectMapper;

    public DocumentGenerationTool(
            DocumentGenerationCapability documentCapability,
            ObjectMapper objectMapper) {
        this.documentCapability = documentCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_DOCUMENT;
    }

    @Override
    public String description() {
        return "当用户明确要求生成 Word 文档或 PDF 文件时调用。"
                + "支持 .docx（Word）和 .pdf（PDF）两种格式，通过文件名扩展名判断格式。"
                + "内容必须完整；不支持读写服务端路径。"
                + "参数必须是单个合法 JSON 对象，不要输出代码块或额外说明文字。"
                + "两种入口互斥：纯文本用 content（\\n 表示换行）；"
                + "长正文优先使用 content。合法示例："
                + "{\"fileName\":\"报告.docx\",\"title\":\"标题\","
                + "\"content\":\"第一段\\n第二段\"}。"
                + "结构化 DOCX 用 sectionsJson 传入 JSON 数组，元素类型支持"
                + "heading_1 / heading_2 / paragraph / bullet_list / ordered_list / table / page_break。"
                + "DocumentSection 的字段：heading_1/heading_2/paragraph 携带 text；"
                + "bullet_list/ordered_list 携带 items 字符串数组；"
                + "table 携带 rows 二维字符串数组；page_break 无字段。";
    }

    @Override
    public Class<DocumentGenerationToolInput> inputType() {
        return DocumentGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return documentCapability.available();
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
            DocumentGenerationToolInput input,
            ToolContext context) {
        String sectionsJson = input.sectionsJson();
        String content = input.content();
        try {
            GeneratedMedia media;
            if (sectionsJson != null && !sectionsJson.isBlank()) {
                if (content != null && !content.isBlank()) {
                    return error("content 与 sectionsJson 不能同时提供");
                }
                DocumentRequest request = parseStructuredRequest(input.title(), sectionsJson);
                media = documentCapability.generate(input.fileName(), request);
            } else {
                media = documentCapability.generate(
                        input.fileName(), input.title(), content);
            }
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "ok");
            result.put("media_type", "file");
            result.put("file_name", media.fileName());
            result.put("bytes", media.bytes().length);
            result.put("message", "文档已生成，将作为微信文件发送");
            return new ToolExecution(result.toString(), media);
        } catch (IllegalArgumentException exception) {
            LOG.warn("document_generation_failed errorType={} reason={}",
                    exception.getClass().getSimpleName(),
                    exception.getClass().getSimpleName());
            return error(
                    exception.getMessage() != null ? exception.getMessage() : "未知错误",
                    true);
        } catch (IOException exception) {
            LOG.warn("document_generation_failed errorType={} reason={}",
                    exception.getClass().getSimpleName(),
                    exception.getClass().getSimpleName());
            return error("文档写入失败，请稍后重试", false);
        }
    }

    private ToolExecution error(String message) {
        return error(message, true);
    }

    private ToolExecution error(String message, boolean retryable) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "error");
        result.put("error_type", "DocumentGenerationError");
        result.put("message", message);
        result.put("retryable", retryable);
        return new ToolExecution(result.toString(), null);
    }

    private DocumentRequest parseStructuredRequest(String title, String sectionsJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(sectionsJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("sectionsJson 不是合法 JSON：" + exception.getMessage(),
                    exception);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("sectionsJson 必须是 JSON 数组");
        }
        ArrayNode array = (ArrayNode) root;
        if (array.size() == 0) {
            throw new IllegalArgumentException("sectionsJson 不能为空数组");
        }
        List<DocumentSection> sections = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            sections.add(parseSection(i, array.get(i)));
        }
        return new DocumentRequest(title, sections);
    }

    private DocumentSection parseSection(int index, JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个 section 不是对象");
        }
        JsonNode typeNode = node.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个 section 缺少 type 字段");
        }
        String type = typeNode.asText();
        try {
            switch (type) {
                case "heading_1":
                    return new DocumentSection.Heading1Section(requireText(node, index, "text"));
                case "heading_2":
                    return new DocumentSection.Heading2Section(requireText(node, index, "text"));
                case "paragraph":
                    return new DocumentSection.ParagraphSection(requireText(node, index, "text"));
                case "bullet_list":
                    return new DocumentSection.BulletListSection(requireStringArray(node, index, "items"));
                case "ordered_list":
                    return new DocumentSection.OrderedListSection(requireStringArray(node, index, "items"));
                case "table":
                    return new DocumentSection.TableSection(requireTableRows(node, index));
                case "page_break":
                    return new DocumentSection.PageBreakSection();
                default:
                    throw new IllegalArgumentException(
                            "第 " + (index + 1) + " 个 section type 不支持：" + type);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
    }

    private String requireText(JsonNode node, int index, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个 section 缺少 " + field + " 字符串字段");
        }
        return value.asText();
    }

    private List<String> requireStringArray(JsonNode node, int index, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个 section 缺少 " + field + " 数组字段");
        }
        List<String> result = new ArrayList<>(value.size());
        for (int i = 0; i < value.size(); i++) {
            JsonNode entry = value.get(i);
            if (entry == null || !entry.isTextual()) {
                throw new IllegalArgumentException(
                        "第 " + (index + 1) + " 个 section " + field
                                + "[" + i + "] 不是字符串");
            }
            result.add(entry.asText());
        }
        return result;
    }

    private List<List<String>> requireTableRows(JsonNode node, int index) {
        JsonNode rows = node.get("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "第 " + (index + 1) + " 个 table section 缺少 rows 二维数组");
        }
        List<List<String>> result = new ArrayList<>(rows.size());
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            if (row == null || !row.isArray()) {
                throw new IllegalArgumentException(
                        "table section 第 " + (rowIndex + 1) + " 行不是数组");
            }
            List<String> line = new ArrayList<>(row.size());
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                JsonNode cell = row.get(colIndex);
                if (cell == null || !cell.isTextual()) {
                    throw new IllegalArgumentException(
                            "table section 第 " + (rowIndex + 1) + " 行第 "
                                    + (colIndex + 1) + " 列不是字符串");
                }
                line.add(cell.asText());
            }
            result.add(line);
        }
        return result;
    }
}
