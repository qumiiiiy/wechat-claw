package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_IMAGE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.tool.input.ImageGenerationToolInput;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that generates one image from an explicit user request.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Order(20)
@Component
public class ImageGenerationTool implements AgentTool<ImageGenerationToolInput> {

    private final MediaCapability mediaCapability;
    private final ObjectMapper objectMapper;

    public ImageGenerationTool(
            MediaCapability mediaCapability,
            ObjectMapper objectMapper) {
        this.mediaCapability = mediaCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_IMAGE;
    }

    @Override
    public String description() {
        return "仅当用户明确要求画图、生成图片、海报或视觉作品时调用。"
                + "把用户要求整理成完整、具体的中文图片描述；普通图片问答不要调用。";
    }

    @Override
    public Class<ImageGenerationToolInput> inputType() {
        return ImageGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return mediaCapability.isImageAvailable();
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
            ImageGenerationToolInput input,
            ToolContext context) {
        GeneratedMedia media = mediaCapability.generateImage(input.prompt());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        result.put("media_type", "image");
        result.put("message", "图片已生成，将作为微信图片发送");
        return new ToolExecution(result.toString(), media);
    }
}
