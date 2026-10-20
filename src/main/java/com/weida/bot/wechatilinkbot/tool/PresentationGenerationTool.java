package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_PRESENTATION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.PresentationCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.PresentationRequest;
import com.weida.bot.wechatilinkbot.model.PresentationSlide;
import com.weida.bot.wechatilinkbot.tool.input.PresentationGenerationToolInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Generates a whole PPTX deck from a natural outline without accepting server paths. */
@Order(47)
@Component
public class PresentationGenerationTool
        implements AgentTool<PresentationGenerationToolInput> {

    private static final int MAX_INPUT_CHARACTERS = 100_000;

    private final PresentationCapability presentationCapability;
    private final ObjectMapper objectMapper;

    public PresentationGenerationTool(
            PresentationCapability presentationCapability,
            ObjectMapper objectMapper) {
        this.presentationCapability = presentationCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_PRESENTATION;
    }

    @Override
    public String description() {
        return "当用户明确要求生成 PPTX 演示文稿时调用。"
                + "输入标题和幻灯片 JSON 数组，支持 title、bullets、table、ending 四种版式；"
                + "不接受输出路径、文件名或任意图片路径，生成后作为微信文件发送。";
    }

    @Override
    public Class<PresentationGenerationToolInput> inputType() {
        return PresentationGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return presentationCapability.available();
    }

    @Override
    public String availabilityReason() {
        return available() ? "available" : "office_presentation_unavailable";
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
            PresentationGenerationToolInput input,
            ToolContext context) {
        try {
            PresentationRequest request = new PresentationRequest(
                    requireText(input.title(), "演示文稿标题"),
                    parseSlides(input.slidesJson()));
            GeneratedMedia media = presentationCapability.generate(request);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "ok");
            result.put("media_type", "file");
            result.put("file_name", media.fileName());
            result.put("bytes", media.bytes().length);
            result.put("message", "演示文稿已生成，将作为微信文件发送");
            return new ToolExecution(result.toString(), media);
        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        } catch (RuntimeException exception) {
            return error("演示文稿生成失败，请检查 Office 配置和提纲内容");
        }
    }

    private List<PresentationSlide> parseSlides(String slidesJson) {
        if (slidesJson == null || slidesJson.isBlank()) {
            throw new IllegalArgumentException("请提供至少一页幻灯片提纲");
        }
        if (slidesJson.length() > MAX_INPUT_CHARACTERS) {
            throw new IllegalArgumentException("幻灯片提纲超过长度限制");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(slidesJson);
        } catch (IOException exception) {
            throw new IllegalArgumentException("幻灯片提纲不是合法 JSON", exception);
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new IllegalArgumentException("幻灯片提纲必须是非空 JSON 数组");
        }
        List<PresentationSlide> slides = new ArrayList<>(root.size());
        for (int index = 0; index < root.size(); index++) {
            slides.add(parseSlide(root.get(index), index + 1));
        }
        return List.copyOf(slides);
    }

    private PresentationSlide parseSlide(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("第 " + index + " 页不是对象");
        }
        String type = text(node, "type", "版式").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "title", "title_slide" -> new PresentationSlide.TitleSlide(
                    text(node, "title", "标题"),
                    optionalText(node, "subtitle"));
            case "bullet", "bullets", "bullet_slide" -> new PresentationSlide.BulletSlide(
                    text(node, "heading", "要点页标题"),
                    stringArray(node, "bullets", "要点"));
            case "table", "table_slide" -> new PresentationSlide.TableSlide(
                    text(node, "heading", "表格页标题"),
                    tableRows(node));
            case "ending", "ending_slide" -> new PresentationSlide.EndingSlide(
                    text(node, "message", "结束语"));
            case "picture", "picture_slide" -> throw new IllegalArgumentException(
                    "图文页需要服务端已批准的图片媒体，当前请求不能直接提供图片路径");
            default -> throw new IllegalArgumentException(
                    "第 " + index + " 页版式不支持：" + type);
        };
    }

    private List<String> stringArray(JsonNode node, String field, String label) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空数组");
        }
        List<String> values = new ArrayList<>(value.size());
        for (JsonNode item : value) {
            if (item == null || !item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(label + "只能包含非空文字");
            }
            values.add(item.asText().strip());
        }
        return List.copyOf(values);
    }

    private List<List<String>> tableRows(JsonNode node) {
        JsonNode value = node.get("rows");
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException("表格行不能为空");
        }
        List<List<String>> rows = new ArrayList<>(value.size());
        for (JsonNode row : value) {
            if (row == null || !row.isArray() || row.isEmpty()) {
                throw new IllegalArgumentException("表格行不能为空");
            }
            List<String> cells = new ArrayList<>(row.size());
            for (JsonNode cell : row) {
                if (cell == null || !cell.isTextual()) {
                    throw new IllegalArgumentException("表格单元格必须是文字");
                }
                cells.add(cell.asText());
            }
            rows.add(List.copyOf(cells));
        }
        return List.copyOf(rows);
    }

    private String text(JsonNode node, String field, String label) {
        String value = optionalText(node, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.asText().strip();
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value;
    }

    private ToolExecution error(String message) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "error");
        result.put("message", message == null || message.isBlank()
                ? "演示文稿生成失败，请稍后重试"
                : message);
        return new ToolExecution(result.toString(), null);
    }
}
