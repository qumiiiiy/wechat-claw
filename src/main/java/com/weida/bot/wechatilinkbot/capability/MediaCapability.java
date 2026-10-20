package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaClient;
import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaProperties;
import com.weida.bot.wechatilinkbot.infrastructure.media.FfmpegMediaConverter;
import com.weida.bot.wechatilinkbot.model.ConvertedVideo;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Provides user-visible image and speech generation through the DashScope adapter.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class MediaCapability {

    private static final Logger LOG = LoggerFactory.getLogger(MediaCapability.class);
    private static final long MINIMUM_SPEECH_DURATION_MILLIS = 1000L;

    private final DashScopeMediaProperties properties;
    private final FfmpegMediaConverter ffmpegMediaConverter;
    private final DashScopeMediaClient mediaClient;
    private final HttpClient httpClient;
    private final Semaphore requestPermits;

    public MediaCapability(
            DashScopeMediaProperties properties,
            FfmpegMediaConverter ffmpegMediaConverter,
            DashScopeMediaClient mediaClient) {
        this.properties = properties;
        this.ffmpegMediaConverter = ffmpegMediaConverter;
        this.mediaClient = mediaClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestPermits = new Semaphore(properties.getMaxConcurrentRequests());
        if (properties.isEnabled() && !hasApiKey()) {
            LOG.warn(
                    "dashscope_media_disabled_missing_api_key environmentVariable=DASHSCOPE_API_KEY");
        }
    }

    /** Returns whether image generation can execute. */
    public boolean isImageAvailable() {
        return properties.isEnabled() && properties.isImageEnabled() && hasApiKey();
    }

    /** Returns whether speech generation and conversion can execute. */
    public boolean isSpeechAvailable() {
        return properties.isEnabled()
                && properties.isSpeechEnabled()
                && hasApiKey()
                && ffmpegMediaConverter.isAvailable();
    }

    /** Generates and downloads one image. */
    public GeneratedMedia generateImage(String prompt) {
        if (!isImageAvailable()) {
            throw new MediaGenerationException("百炼图片生成未启用或缺少 API Key");
        }
        String safePrompt = requireText(
                prompt, "图片描述", properties.getImageMaxPromptCharacters());
        long startNanos = System.nanoTime();
        LOG.info(
                "dashscope_image_generation_started model={} promptLength={}",
                properties.getImageModel(),
                safePrompt.length());
        GeneratedMedia result = withPermit(() -> requestImage(safePrompt));
        LOG.info(
                "dashscope_image_generation_completed model={} bytes={} elapsedMillis={}",
                properties.getImageModel(),
                result.bytes().length,
                elapsedMillis(startNanos));
        return result;
    }

    /** Synthesizes speech and converts it to MP4. */
    public GeneratedMedia generateSpeechVideo(String text) {
        return generateSpeechVideo(text, VoicePreference.DEFAULT);
    }

    /** Synthesizes speech with a provider-independent voice preference. */
    public GeneratedMedia generateSpeechVideo(
            String text,
            VoicePreference voicePreference) {
        if (!isSpeechAvailable()) {
            if (!ffmpegMediaConverter.isAvailable()) {
                throw new MediaGenerationException(
                        "FFmpeg 不可用，无法将百炼语音封装为微信可发送的 MP4");
            }
            throw new MediaGenerationException("百炼语音合成未启用或缺少 API Key");
        }
        String safeText = requireText(
                text, "待合成文字", properties.getSpeechMaxCharacters());
        String voice = resolveVoice(voicePreference);
        long startNanos = System.nanoTime();
        LOG.info(
                "dashscope_speech_generation_started model={} voice={} textLength={}",
                properties.getSpeechModel(),
                voice,
                safeText.length());
        GeneratedMedia result = withPermit(() -> requestSpeech(safeText, voice));
        LOG.info(
                "dashscope_speech_generation_completed model={} voice={} bytes={} durationMillis={} "
                        + "elapsedMillis={}",
                properties.getSpeechModel(),
                voice,
                result.bytes().length,
                result.durationMillis(),
                elapsedMillis(startNanos));
        return result;
    }

    private GeneratedMedia requestImage(String prompt)
            throws IOException, InterruptedException {
        DownloadedMedia image = download(mediaClient.generateImage(prompt));
        String mimeType = detectImageMimeType(image.bytes(), image.contentType());
        return new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                image.bytes(),
                "qwen-image." + imageExtension(mimeType),
                0L,
                "");
    }

    private GeneratedMedia requestSpeech(String text, String voice)
            throws IOException, InterruptedException {
        byte[] audioBytes = mediaClient.synthesizeSpeech(text, voice);
        if (audioBytes.length == 0 || audioBytes.length > properties.getMaxDownloadBytes()) {
            throw new IOException("百炼语音 SDK 返回的音频为空或超过大小限制");
        }
        ConvertedVideo video = ffmpegMediaConverter.convert(
                audioBytes,
                properties.getSpeechFormat(),
                estimateSpeechDuration(text));
        return new GeneratedMedia(
                GeneratedMediaKind.SPEECH_VIDEO,
                video.bytes(),
                "qwen-speech.mp4",
                video.durationMillis(),
                "");
    }

    private DownloadedMedia download(URI uri) throws IOException, InterruptedException {
        URI downloadUri = normalizeTrustedDownloadUri(uri);
        HttpRequest request = HttpRequest.newBuilder(downloadUri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "*/*")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            validateTrustedDownloadUri(response.uri());
            verifyDownloadResponse(response);
            byte[] bytes = input.readNBytes(properties.getMaxDownloadBytes() + 1);
            validateDownloadBytes(bytes);
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");
            return new DownloadedMedia(bytes, contentType);
        }
    }

    private void verifyDownloadResponse(HttpResponse<InputStream> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载百炼媒体失败：HTTP " + response.statusCode());
        }
        long contentLength = response.headers()
                .firstValueAsLong("Content-Length")
                .orElse(-1L);
        if (contentLength > properties.getMaxDownloadBytes()) {
            throw new IOException("百炼媒体超过下载大小限制：" + contentLength + " bytes");
        }
    }

    private void validateDownloadBytes(byte[] bytes) throws IOException {
        if (bytes.length == 0) {
            throw new IOException("百炼媒体下载结果为空");
        }
        if (bytes.length > properties.getMaxDownloadBytes()) {
            throw new IOException("百炼媒体超过下载大小限制");
        }
    }

    private void validateTrustedDownloadUri(URI uri) throws IOException {
        if (!isTrustedDownloadUri(uri)) {
            String host = uri == null || uri.getHost() == null
                    ? "unknown"
                    : uri.getHost();
            throw new IOException("百炼返回了不受信任的媒体下载地址，host=" + host);
        }
    }

    URI normalizeTrustedDownloadUri(URI uri) throws IOException {
        if (uri == null || uri.getHost() == null) {
            throw new IOException("百炼返回了无效的媒体下载地址");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("http".equalsIgnoreCase(uri.getScheme())
                && isTrustedAliyunHost(host)) {
            return URI.create("https:" + uri.toASCIIString().substring(
                    uri.getScheme().length() + 1));
        }
        validateTrustedDownloadUri(uri);
        return uri;
    }

    private boolean isTrustedDownloadUri(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean trustedHost = isTrustedAliyunHost(host) || isConfiguredApiHost(host);
        if (!trustedHost) {
            return false;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        return "http".equalsIgnoreCase(uri.getScheme()) && isLoopbackHost(host);
    }

    private boolean isConfiguredApiHost(String host) {
        String apiHost = properties.getBaseUrl().getHost();
        return apiHost != null && host.equals(apiHost.toLowerCase(Locale.ROOT));
    }

    private static boolean isTrustedAliyunHost(String host) {
        return host.endsWith(".aliyuncs.com");
    }

    private <T> T withPermit(InterruptibleSupplier<T> action) {
        boolean acquired = false;
        try {
            requestPermits.acquire();
            acquired = true;
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaGenerationException("等待百炼媒体生成时被中断", exception);
        } catch (IOException exception) {
            throw new MediaGenerationException(
                    "调用百炼媒体模型失败：" + exception.getMessage(), exception);
        } finally {
            if (acquired) {
                requestPermits.release();
            }
        }
    }

    private long estimateSpeechDuration(String text) {
        int characterCount = text.codePointCount(0, text.length());
        return Math.max(
                MINIMUM_SPEECH_DURATION_MILLIS,
                Math.round(characterCount * 260.0 / properties.getSpeechRate()));
    }

    private boolean hasApiKey() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    private String resolveVoice(VoicePreference voicePreference) {
        VoicePreference safePreference = voicePreference == null
                ? VoicePreference.DEFAULT
                : voicePreference;
        return switch (safePreference) {
            case MALE -> properties.getSpeechMaleVoice().strip();
            case FEMALE -> properties.getSpeechFemaleVoice().strip();
            case CHILD -> properties.getSpeechChildVoice().strip();
            case DEFAULT -> properties.getSpeechVoice().strip();
            default -> resolveProfileVoice(safePreference);
        };
    }

    private String resolveProfileVoice(VoicePreference preference) {
        String voice = properties.getSpeechProfileVoice().get(preference.getConfigKey());
        if (voice == null || voice.isBlank()) {
            throw new MediaGenerationException(
                    "未配置音色画像：" + preference.getConfigKey());
        }
        return voice.strip();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static String requireText(String value, String field, int maxCharacters) {
        if (value == null || value.isBlank()) {
            throw new MediaGenerationException(field + "不能为空");
        }
        String safeValue = value.strip();
        if (safeValue.length() > maxCharacters) {
            throw new MediaGenerationException(
                    field + "超过 " + maxCharacters + " 个字符");
        }
        return safeValue;
    }

    private static String detectImageMimeType(byte[] bytes, String contentType) {
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (isWebp(bytes)) {
            return "image/webp";
        }
        String safeContentType = contentType == null
                ? ""
                : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if ("image/png".equals(safeContentType)
                || "image/jpeg".equals(safeContentType)
                || "image/webp".equals(safeContentType)) {
            return safeContentType;
        }
        throw new MediaGenerationException("百炼返回的文件不是可识别的图片");
    }

    private static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private static String imageExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLoopbackHost(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
    }

    @FunctionalInterface
    private interface InterruptibleSupplier<T> {
        T get() throws IOException, InterruptedException;
    }

    private record DownloadedMedia(byte[] bytes, String contentType) {

        private DownloadedMedia {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
