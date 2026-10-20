package com.weida.bot.wechatilinkbot.infrastructure.media;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Narrow adapter around the official Alibaba Cloud DashScope Java SDK.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Component
public class DashScopeMediaClient {

    private static final String HTTP_PROTOCOL = "http";

    private final DashScopeMediaProperties properties;
    private final ConnectionOptions connectionOptions;

    public DashScopeMediaClient(DashScopeMediaProperties properties) {
        this.properties = properties;
        this.connectionOptions = ConnectionOptions.builder()
                .connectTimeout(properties.getConnectTimeout())
                .writeTimeout(properties.getRequestTimeout())
                .readTimeout(properties.getRequestTimeout())
                .build();
    }

    /**
     * Generates one image and returns its short-lived DashScope download URL.
     *
     * @param prompt validated image prompt
     * @return SDK result image URL
     * @throws IOException when the SDK response is incomplete or invalid
     */
    public URI generateImage(String prompt) throws IOException {
        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role("user")
                .content(List.of(Map.of("text", prompt)))
                .build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(properties.getApiKey().strip())
                .model(properties.getImageModel())
                .messages(List.of(userMessage))
                .size(properties.getImageSize())
                .n(1)
                .promptExtend(properties.isImagePromptExtend())
                .watermark(properties.isImageWatermark())
                .build();
        try {
            MultiModalConversation conversation = new MultiModalConversation(
                    HTTP_PROTOCOL,
                    properties.getBaseUrl().toString(),
                    connectionOptions);
            MultiModalConversationResult result = conversation.call(param);
            return extractImageUri(result);
        } catch (NoApiKeyException | UploadFileException exception) {
            throw sanitizedSdkFailure("百炼图片 SDK 调用失败", exception);
        } catch (RuntimeException exception) {
            throw sanitizedSdkFailure("百炼图片 SDK 调用失败", exception);
        }
    }

    /**
     * Synthesizes speech bytes through DashScope's CosyVoice WebSocket SDK.
     *
     * @param text validated synthesis text
     * @param voice configured DashScope voice
     * @return complete encoded audio bytes
     * @throws IOException when the SDK response is empty or invalid
     */
    public byte[] synthesizeSpeech(String text, String voice) throws IOException {
        SpeechSynthesisAudioFormat audioFormat = resolveAudioFormat();
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .apiKey(properties.getApiKey().strip())
                .model(properties.getSpeechModel())
                .voice(voice)
                .format(audioFormat)
                .volume(properties.getSpeechVolume())
                .speechRate((float) properties.getSpeechRate())
                .pitchRate((float) properties.getSpeechPitch())
                .build();
        try {
            SpeechSynthesizer synthesizer = new SpeechSynthesizer(
                    param,
                    null,
                    properties.getSpeechWebSocketUrl().toString(),
                    connectionOptions);
            ByteBuffer audioBuffer = synthesizer.call(text);
            if (audioBuffer == null || !audioBuffer.hasRemaining()) {
                throw new IOException("百炼语音 SDK 返回了空音频");
            }
            ByteBuffer safeBuffer = audioBuffer.asReadOnlyBuffer();
            byte[] audioBytes = new byte[safeBuffer.remaining()];
            safeBuffer.get(audioBytes);
            return audioBytes;
        } catch (RuntimeException exception) {
            throw sanitizedSdkFailure("百炼语音 SDK 调用失败", exception);
        }
    }

    private URI extractImageUri(MultiModalConversationResult result) throws IOException {
        if (result == null || result.getOutput() == null) {
            throw new IOException("百炼图片 SDK 返回了空响应");
        }
        MultiModalConversationOutput output = result.getOutput();
        if (output.getChoices() == null || output.getChoices().isEmpty()) {
            throw new IOException("百炼图片 SDK 响应中缺少 choices");
        }
        MultiModalMessage message = output.getChoices().getFirst().getMessage();
        if (message == null || message.getContent() == null) {
            throw new IOException("百炼图片 SDK 响应中缺少图片内容");
        }
        for (Map<String, Object> content : message.getContent()) {
            Object image = content.get("image");
            if (image instanceof String imageUrl && !imageUrl.isBlank()) {
                return URI.create(imageUrl);
            }
        }
        throw new IOException("百炼图片 SDK 响应中缺少图片地址");
    }

    private SpeechSynthesisAudioFormat resolveAudioFormat() {
        String configuredFormat = properties.getSpeechFormat().strip();
        int configuredSampleRate = properties.getSpeechSampleRate();
        for (SpeechSynthesisAudioFormat format : SpeechSynthesisAudioFormat.values()) {
            if (configuredFormat.equalsIgnoreCase(format.getFormat())
                    && configuredSampleRate == format.getSampleRate()) {
                return format;
            }
        }
        throw new IllegalArgumentException(
                "不支持的百炼语音格式和采样率组合："
                        + configuredFormat
                        + "/"
                        + configuredSampleRate);
    }

    private static IOException sanitizedSdkFailure(
            String message,
            Exception exception) {
        IOException safeException = new IOException(message);
        safeException.setStackTrace(exception.getStackTrace());
        return safeException;
    }
}
