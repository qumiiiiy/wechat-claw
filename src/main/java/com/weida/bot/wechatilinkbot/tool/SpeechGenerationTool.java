package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPEECH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import com.weida.bot.wechatilinkbot.tool.input.SpeechGenerationToolInput;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Agent tool that synthesizes speech and delivers it as an MP4 video.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Order(30)
@Component
public class SpeechGenerationTool implements AgentTool<SpeechGenerationToolInput> {

    private final MediaCapability mediaCapability;
    private final ObjectMapper objectMapper;

    public SpeechGenerationTool(
            MediaCapability mediaCapability,
            ObjectMapper objectMapper) {
        this.mediaCapability = mediaCapability;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return GENERATE_SPEECH;
    }

    @Override
    public String description() {
        return "当用户表达希望听到助手声音的意愿时调用，包括语音回复、朗读、配音或演唱。"
                + "微信机器人不能发送语音气泡，本工具会把适合直接朗读的正文封装为 MP4。"
                + "同一次请求只调用一次，完成后不要描述生成、合成、转换或发送过程。";
    }

    @Override
    public Class<SpeechGenerationToolInput> inputType() {
        return SpeechGenerationToolInput.class;
    }

    @Override
    public boolean available() {
        return mediaCapability.isSpeechAvailable();
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
            SpeechGenerationToolInput input,
            ToolContext context) {
        VoicePreference voicePreference = parseVoicePreference(
                input.voice());
        GeneratedMedia media = mediaCapability.generateSpeechVideo(
                input.text(), voicePreference);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("status", "ok");
        result.put("media_type", "mp4");
        result.put("voice", voicePreference.name().toLowerCase(Locale.ROOT));
        result.put("message", "语音已封装为 MP4，将作为微信视频发送");
        return new ToolExecution(result.toString(), media);
    }

    private static VoicePreference parseVoicePreference(String value) {
        return switch (value == null ? "" : value.strip().toLowerCase(Locale.ROOT)) {
            case "male" -> VoicePreference.MALE;
            case "female" -> VoicePreference.FEMALE;
            default -> VoicePreference.DEFAULT;
        };
    }
}
