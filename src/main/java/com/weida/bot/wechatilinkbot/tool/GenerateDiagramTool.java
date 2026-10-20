package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DIAGRAM;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.DiagramCapability;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.DiagramToolInput;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that renders a PlantUML source into a PNG image and returns it
 * through the channel.
 *
 * <p>When PlantUML rendering fails on this Windows host (Chocolatey install
 * has known issues with the {@code -pipe} mode and occasionally hangs file
 * mode), the tool falls back to a {@code qwen-image} generated illustration
 * so the user always receives something visual.</p>
 *
 * @author ryx
 * @since 2026-07-24
 */
@Order(40)
@Component
public class GenerateDiagramTool implements AgentTool<DiagramToolInput> {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateDiagramTool.class);

    /**
     * Diagram error codes that mean the user's input is bad, not that
     * PlantUML had a transient failure. Retrying or falling back to
     * {@code qwen-image} cannot fix them — propagate the error so the user
     * sees a meaningful message.
     *
     * <p>Values mirror the DiagramErrorCode constants in the diagram
     * adapter package; we use string literals here to keep the tool module
     * independent of that package per the architecture rules.</p>
     */
    private static final Set<String> NON_FALLBACK_ERROR_CODES = Set.of(
            "diagram_disabled",
            "diagram_executable_missing",
            "diagram_source_required",
            "diagram_source_too_long",
            "diagram_invalid_type",
            "diagram_forbidden_directive");

    private final DiagramCapability capability;
    private final MediaCapability mediaCapability;
    private final ObjectMapper objectMapper;

    public GenerateDiagramTool(
            DiagramCapability capability,
            MediaCapability mediaCapability,
            ObjectMapper objectMapper) {
        this.capability = capability;
        this.mediaCapability = mediaCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_DIAGRAM;
    }

    @Override
    public String description() {
        return "仅当用户明确要求画流程图、时序图、类图、状态机、ER 图、思维导图或类似 "
                + "PlantUML 支持的图时调用。"
                + "把用户的描述整理成 PlantUML 源码（必须以 @startuml 开头、@enduml 结尾），"
                + "并在 type 字段给出具体图类型；"
                + "普通问答、表格、数学公式或不需要图的任务不要调用。";
    }

    @Override
    public Class<DiagramToolInput> inputType() {
        return DiagramToolInput.class;
    }

    @Override
    public boolean available() {
        return capability.isAvailable();
    }

    @Override
    public String availabilityReason() {
        return capability.availabilityReason();
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
    public ToolExecution execute(DiagramToolInput input, ToolContext context) {
        String normalizedType = input.diagramType() == null
                ? ""
                : input.diagramType().strip().toLowerCase(Locale.ROOT);
        String source = input.source() == null ? "" : input.source();
        try {
            GeneratedMedia media = capability.generateDiagram(normalizedType, source);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "ok");
            result.put("media_type", "image");
            result.put("diagram_type", normalizedType);
            result.put("message", "图表已生成，将作为微信图片发送");
            return new ToolExecution(result.toString(), media);
        } catch (RuntimeException exception) {
            return handleDiagramFailure(exception, normalizedType, context);
        }
    }

    /**
     * Recover from PlantUML failure by asking qwen-image for an illustrative
     * picture. Only retries (within the renderer) won't help for transient
     * "No diagram found" or process-hang cases on this host — the next launch
     * is just as likely to fail. A different generative path gives the user
     * a visible result instead of a 4-minute timeout.
     */
    private ToolExecution handleDiagramFailure(
            RuntimeException exception,
            String diagramType,
            ToolContext context) {
        String code = readErrorCode(exception);
        if (NON_FALLBACK_ERROR_CODES.contains(code)) {
            throw exception;
        }
        if (!mediaCapability.isImageAvailable()) {
            LOG.warn(
                    "diagram_fallback_unavailable reason=image_capability_disabled code={}",
                    code);
            throw exception;
        }
        String fallbackPrompt = buildFallbackPrompt(context.currentUserText(), diagramType);
        LOG.warn(
                "diagram_render_falling_back_to_qwen_image code={} reason={} promptLength={}",
                code,
                exception.getMessage(),
                fallbackPrompt.length());
        try {
            GeneratedMedia fallback = mediaCapability.generateImage(fallbackPrompt);
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "fallback");
            result.put("media_type", "image");
            result.put("diagram_type", diagramType);
            result.put("original_error_code", code);
            result.put("message",
                    "PlantUML 渲染失败，已改用通用图像模型生成可视化图");
            return new ToolExecution(result.toString(), fallback);
        } catch (RuntimeException fallbackError) {
            LOG.error(
                    "diagram_fallback_failed originalCode={} fallbackError={}",
                    code,
                    fallbackError.getMessage(),
                    fallbackError);
            throw exception;
        }
    }

    /**
     * DiagramException exposes a {@code getCode()} method. To avoid an
     * explicit dependency from the tool module to the diagram adapter
     * package, we read it reflectively. Any other RuntimeException is
     * treated as an unknown error and is always eligible for fallback.
     */
    private static String readErrorCode(RuntimeException exception) {
        try {
            Object code = exception.getClass()
                    .getMethod("getCode")
                    .invoke(exception);
            return code == null ? "" : code.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static String buildFallbackPrompt(String userText, String diagramType) {
        String safeUserText = userText == null ? "" : userText.strip();
        String typeLabel = diagramType == null || diagramType.isBlank()
                ? "信息图"
                : switch (diagramType) {
                    case "mindmap", "erd", "flowchart", "sequence", "class",
                            "state", "activity", "usecase", "component",
                            "deployment", "object", "timing", "gantt",
                            "wbs", "salt" -> diagramType;
                    default -> "信息图";
                };
        if (safeUserText.isEmpty()) {
            return "Generate a clean, professional " + typeLabel + " illustration";
        }
        return "Generate a clean, professional " + typeLabel
                + " illustration based on this request: " + safeUserText;
    }
}
