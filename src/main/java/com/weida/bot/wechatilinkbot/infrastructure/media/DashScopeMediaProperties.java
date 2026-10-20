package com.weida.bot.wechatilinkbot.infrastructure.media;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated configuration for DashScope image, speech, download, and FFmpeg limits.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Validated
@ConfigurationProperties(prefix = "bot.infrastructure.media.dashscope")
public class DashScopeMediaProperties {

    private boolean enabled = true;
    private String apiKey = "";

    @NotNull
    private URI baseUrl = URI.create("https://dashscope.aliyuncs.com/api/v1");

    @NotNull
    private URI speechWebSocketUrl =
            URI.create("wss://dashscope.aliyuncs.com/api-ws/v1/inference");

    private boolean speechEnabled = true;

    @NotBlank
    private String speechModel = "cosyvoice-v3-flash";

    @NotBlank
    private String speechVoice = "longanyang";

    @NotBlank
    private String speechMaleVoice = "longanyang";

    @NotBlank
    private String speechFemaleVoice = "longanhuan_v3";

    @NotBlank
    private String speechChildVoice = "longhuhu_v3";

    @NotNull
    private Map<String, String> speechProfileVoice = defaultSpeechProfileVoice();

    @NotBlank
    private String speechFormat = "mp3";

    @Min(8000)
    @Max(48000)
    private int speechSampleRate = 24000;

    @Min(0)
    @Max(100)
    private int speechVolume = 50;

    @DecimalMin("0.5")
    @DecimalMax("2.0")
    private double speechRate = 1.0;

    @DecimalMin("0.5")
    @DecimalMax("2.0")
    private double speechPitch = 1.0;

    @Min(1)
    @Max(10000)
    private int speechMaxCharacters = 2000;

    private boolean imageEnabled = true;

    @NotBlank
    private String imageModel = "qwen-image-2.0";

    @NotBlank
    private String imageSize = "1024*1024";

    private boolean imagePromptExtend = true;
    private boolean imageWatermark;

    @Min(1)
    @Max(10000)
    private int imageMaxPromptCharacters = 4000;

    @Min(1024)
    @Max(104857600)
    private int maxDownloadBytes = 20 * 1024 * 1024;

    @Min(1)
    @Max(8)
    private int maxConcurrentRequests = 2;

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(15);

    @NotNull
    private Duration requestTimeout = Duration.ofMinutes(3);

    @NotBlank
    private String ffmpegExecutable = "ffmpeg";

    @NotBlank
    private String ffprobeExecutable = "ffprobe";

    @NotNull
    private Duration conversionTimeout = Duration.ofMinutes(2);

    @Min(160)
    @Max(1920)
    private int videoWidth = 640;

    @Min(160)
    @Max(1080)
    private int videoHeight = 360;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public URI getSpeechWebSocketUrl() {
        return speechWebSocketUrl;
    }

    public void setSpeechWebSocketUrl(URI speechWebSocketUrl) {
        this.speechWebSocketUrl = speechWebSocketUrl;
    }

    public boolean isSpeechEnabled() {
        return speechEnabled;
    }

    public void setSpeechEnabled(boolean speechEnabled) {
        this.speechEnabled = speechEnabled;
    }

    public String getSpeechModel() {
        return speechModel;
    }

    public void setSpeechModel(String speechModel) {
        this.speechModel = speechModel;
    }

    public String getSpeechVoice() {
        return speechVoice;
    }

    public void setSpeechVoice(String speechVoice) {
        this.speechVoice = speechVoice;
    }

    public String getSpeechMaleVoice() {
        return speechMaleVoice;
    }

    public void setSpeechMaleVoice(String speechMaleVoice) {
        this.speechMaleVoice = speechMaleVoice;
    }

    public String getSpeechFemaleVoice() {
        return speechFemaleVoice;
    }

    public void setSpeechFemaleVoice(String speechFemaleVoice) {
        this.speechFemaleVoice = speechFemaleVoice;
    }

    public String getSpeechChildVoice() {
        return speechChildVoice;
    }

    public void setSpeechChildVoice(String speechChildVoice) {
        this.speechChildVoice = speechChildVoice;
    }

    public Map<String, String> getSpeechProfileVoice() {
        return speechProfileVoice;
    }

    public void setSpeechProfileVoice(Map<String, String> speechProfileVoice) {
        this.speechProfileVoice = speechProfileVoice == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(speechProfileVoice);
    }

    public String getSpeechFormat() {
        return speechFormat;
    }

    public void setSpeechFormat(String speechFormat) {
        this.speechFormat = speechFormat;
    }

    public int getSpeechSampleRate() {
        return speechSampleRate;
    }

    public void setSpeechSampleRate(int speechSampleRate) {
        this.speechSampleRate = speechSampleRate;
    }

    public int getSpeechVolume() {
        return speechVolume;
    }

    public void setSpeechVolume(int speechVolume) {
        this.speechVolume = speechVolume;
    }

    public double getSpeechRate() {
        return speechRate;
    }

    public void setSpeechRate(double speechRate) {
        this.speechRate = speechRate;
    }

    public double getSpeechPitch() {
        return speechPitch;
    }

    public void setSpeechPitch(double speechPitch) {
        this.speechPitch = speechPitch;
    }

    public int getSpeechMaxCharacters() {
        return speechMaxCharacters;
    }

    public void setSpeechMaxCharacters(int speechMaxCharacters) {
        this.speechMaxCharacters = speechMaxCharacters;
    }

    public boolean isImageEnabled() {
        return imageEnabled;
    }

    public void setImageEnabled(boolean imageEnabled) {
        this.imageEnabled = imageEnabled;
    }

    public String getImageModel() {
        return imageModel;
    }

    public void setImageModel(String imageModel) {
        this.imageModel = imageModel;
    }

    public String getImageSize() {
        return imageSize;
    }

    public void setImageSize(String imageSize) {
        this.imageSize = imageSize;
    }

    public boolean isImagePromptExtend() {
        return imagePromptExtend;
    }

    public void setImagePromptExtend(boolean imagePromptExtend) {
        this.imagePromptExtend = imagePromptExtend;
    }

    public boolean isImageWatermark() {
        return imageWatermark;
    }

    public void setImageWatermark(boolean imageWatermark) {
        this.imageWatermark = imageWatermark;
    }

    public int getImageMaxPromptCharacters() {
        return imageMaxPromptCharacters;
    }

    public void setImageMaxPromptCharacters(int imageMaxPromptCharacters) {
        this.imageMaxPromptCharacters = imageMaxPromptCharacters;
    }

    public int getMaxDownloadBytes() {
        return maxDownloadBytes;
    }

    public void setMaxDownloadBytes(int maxDownloadBytes) {
        this.maxDownloadBytes = maxDownloadBytes;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getFfmpegExecutable() {
        return ffmpegExecutable;
    }

    public void setFfmpegExecutable(String ffmpegExecutable) {
        this.ffmpegExecutable = ffmpegExecutable;
    }

    public String getFfprobeExecutable() {
        return ffprobeExecutable;
    }

    public void setFfprobeExecutable(String ffprobeExecutable) {
        this.ffprobeExecutable = ffprobeExecutable;
    }

    public Duration getConversionTimeout() {
        return conversionTimeout;
    }

    public void setConversionTimeout(Duration conversionTimeout) {
        this.conversionTimeout = conversionTimeout;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    private static Map<String, String> defaultSpeechProfileVoice() {
        Map<String, String> voiceMap = new LinkedHashMap<>();
        voiceMap.put("boy", "longhuhu_v3");
        voiceMap.put("girl", "longhuhu_v3");
        voiceMap.put("young-male", "longanlang_v3");
        voiceMap.put("young-female", "longanhuan");
        voiceMap.put("mature-male", "longanzhi_v3");
        voiceMap.put("mature-female", "longanya_v3");
        voiceMap.put("elderly-male", "longlaobo_v3");
        voiceMap.put("elderly-female", "longlaoyi_v3");
        voiceMap.put("energetic-female", "longanqin_v3");
        voiceMap.put("sweet-female", "longyingxiao_v3");
        voiceMap.put("gentle-female", "longyingtao_v3");
        voiceMap.put("warm-male", "longanyun_v3");
        voiceMap.put("intellectual-female", "longanwen_v3");
        voiceMap.put("narrator-male", "longyichen_v3");
        voiceMap.put("narrator-female", "longwanjun_v3");
        voiceMap.put("professional-female", "loongbella_v3");
        voiceMap.put("live-stream-female", "longanxuan_v3");
        voiceMap.put("robot", "longjiqi_v3");
        voiceMap.put("monkey-king", "longhouge_v3");
        voiceMap.put("cantonese-male", "longanyue_v3");
        voiceMap.put("shaanxi-male", "longshange_v3");
        voiceMap.put("minnan-female", "longanmin_v3");
        return voiceMap;
    }
}
