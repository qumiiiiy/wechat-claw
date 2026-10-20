package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounded, opt-in policy for the local OCS companion bridge. */
@Validated
@ConfigurationProperties(prefix = "bot.capability.learning.ocs")
public class OcsStudyProperties {

    private static final Duration MAX_SESSION_EXPIRE_AFTER = Duration.ofHours(24);

    private boolean enabled;
    private String chaoxingStartUrl = "https://mooc1.chaoxing.com/mycourse";
    private String launchPageUrl = "http://127.0.0.1:8080/ocs/launch.html";
    private List<String> allowedConversationList = List.of();
    private List<String> allowedUrlHostList = List.of();

    @Min(1)
    @Max(100)
    private int maxSessions = 20;

    @Min(1)
    @Max(1_000)
    private int maxAnswerAttemptsPerSession = 200;

    @Min(256)
    @Max(20_000)
    private int maxEventCharacters = 4_000;

    @Min(1_000)
    @Max(100_000)
    private int maxNotesCharacters = 20_000;

    @NotNull
    private Duration sessionExpireAfter = Duration.ofHours(12);

    @NotNull
    private Duration commandLeaseDuration = Duration.ofSeconds(30);

    @NotNull
    private Duration pluginDetectionTimeout = Duration.ofSeconds(10);

    private String pluginVersionMin = "0.6.7";

    private boolean ocsConfigVideoReview = true;

    private boolean ocsConfigAutoChapterSwitch = true;

    private String ocsConfigPlaybackRate = "1";

    private boolean ocsConfigAnswerEnabled = true;

    private String ocsConfigQuestionBankUrl =
            "http://127.0.0.1:8080/api/local/ocs/questions/answer";

    private boolean loginQrRelayEnabled = true;

    @NotNull
    private Duration loginQrTimeout = Duration.ofSeconds(120);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChaoxingStartUrl() {
        return chaoxingStartUrl;
    }

    public void setChaoxingStartUrl(String chaoxingStartUrl) {
        this.chaoxingStartUrl = chaoxingStartUrl == null ? "" : chaoxingStartUrl.strip();
    }

    public String getLaunchPageUrl() {
        return launchPageUrl;
    }

    public void setLaunchPageUrl(String launchPageUrl) {
        this.launchPageUrl = launchPageUrl == null ? "" : launchPageUrl.strip();
    }

    public List<String> getAllowedConversationList() {
        return allowedConversationList;
    }

    public void setAllowedConversationList(List<String> allowedConversationList) {
        this.allowedConversationList = copy(allowedConversationList);
    }

    public List<String> getAllowedUrlHostList() {
        return allowedUrlHostList;
    }

    public void setAllowedUrlHostList(List<String> allowedUrlHostList) {
        this.allowedUrlHostList = copy(allowedUrlHostList);
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public int getMaxAnswerAttemptsPerSession() {
        return maxAnswerAttemptsPerSession;
    }

    public void setMaxAnswerAttemptsPerSession(int maxAnswerAttemptsPerSession) {
        this.maxAnswerAttemptsPerSession = maxAnswerAttemptsPerSession;
    }

    public int getMaxEventCharacters() {
        return maxEventCharacters;
    }

    public void setMaxEventCharacters(int maxEventCharacters) {
        this.maxEventCharacters = maxEventCharacters;
    }

    public int getMaxNotesCharacters() {
        return maxNotesCharacters;
    }

    public void setMaxNotesCharacters(int maxNotesCharacters) {
        this.maxNotesCharacters = maxNotesCharacters;
    }

    public Duration getSessionExpireAfter() {
        return sessionExpireAfter;
    }

    public void setSessionExpireAfter(Duration sessionExpireAfter) {
        this.sessionExpireAfter = sessionExpireAfter;
    }

    public Duration getCommandLeaseDuration() {
        return commandLeaseDuration;
    }

    public void setCommandLeaseDuration(Duration commandLeaseDuration) {
        this.commandLeaseDuration = commandLeaseDuration;
    }

    public Duration getPluginDetectionTimeout() {
        return pluginDetectionTimeout;
    }

    public void setPluginDetectionTimeout(Duration pluginDetectionTimeout) {
        this.pluginDetectionTimeout = pluginDetectionTimeout;
    }

    public String getPluginVersionMin() {
        return pluginVersionMin;
    }

    public void setPluginVersionMin(String pluginVersionMin) {
        this.pluginVersionMin = pluginVersionMin == null ? "" : pluginVersionMin.strip();
    }

    public boolean isOcsConfigVideoReview() {
        return ocsConfigVideoReview;
    }

    public void setOcsConfigVideoReview(boolean ocsConfigVideoReview) {
        this.ocsConfigVideoReview = ocsConfigVideoReview;
    }

    public boolean isOcsConfigAutoChapterSwitch() {
        return ocsConfigAutoChapterSwitch;
    }

    public void setOcsConfigAutoChapterSwitch(boolean ocsConfigAutoChapterSwitch) {
        this.ocsConfigAutoChapterSwitch = ocsConfigAutoChapterSwitch;
    }

    public String getOcsConfigPlaybackRate() {
        return ocsConfigPlaybackRate;
    }

    public void setOcsConfigPlaybackRate(String ocsConfigPlaybackRate) {
        this.ocsConfigPlaybackRate = ocsConfigPlaybackRate == null ? "1" : ocsConfigPlaybackRate.strip();
    }

    public boolean isOcsConfigAnswerEnabled() {
        return ocsConfigAnswerEnabled;
    }

    public void setOcsConfigAnswerEnabled(boolean ocsConfigAnswerEnabled) {
        this.ocsConfigAnswerEnabled = ocsConfigAnswerEnabled;
    }

    public String getOcsConfigQuestionBankUrl() {
        return ocsConfigQuestionBankUrl;
    }

    public void setOcsConfigQuestionBankUrl(String ocsConfigQuestionBankUrl) {
        this.ocsConfigQuestionBankUrl = ocsConfigQuestionBankUrl == null
                ? "" : ocsConfigQuestionBankUrl.strip();
    }

    public boolean isLoginQrRelayEnabled() {
        return loginQrRelayEnabled;
    }

    public void setLoginQrRelayEnabled(boolean loginQrRelayEnabled) {
        this.loginQrRelayEnabled = loginQrRelayEnabled;
    }

    public Duration getLoginQrTimeout() {
        return loginQrTimeout;
    }

    public void setLoginQrTimeout(Duration loginQrTimeout) {
        this.loginQrTimeout = loginQrTimeout;
    }

    @AssertTrue(message = "OCS 学习会话有效期必须在 1 分钟到 24 小时之间")
    public boolean isSessionExpiryValid() {
        return sessionExpireAfter != null
                && sessionExpireAfter.compareTo(Duration.ofMinutes(1)) >= 0
                && sessionExpireAfter.compareTo(MAX_SESSION_EXPIRE_AFTER) <= 0;
    }

    @AssertTrue(message = "OCS 学习通入口必须是无用户信息的 HTTPS 网址")
    public boolean isChaoxingStartUrlValid() {
        if (!enabled) {
            return true;
        }
        try {
            URI uri = URI.create(chaoxingStartUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "OCS 启动页必须是回环地址上的 HTTP 网址")
    public boolean isLaunchPageUrlValid() {
        if (!enabled) {
            return true;
        }
        try {
            URI uri = URI.create(launchPageUrl);
            String host = uri.getHost();
            return "http".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @AssertTrue(message = "OCS 命令租约必须在 5 秒到 2 分钟之间")
    public boolean isCommandLeaseDurationValid() {
        return commandLeaseDuration != null
                && commandLeaseDuration.compareTo(Duration.ofSeconds(5)) >= 0
                && commandLeaseDuration.compareTo(Duration.ofMinutes(2)) <= 0;
    }

    @AssertTrue(message = "OCS 插件检测超时必须在 3 秒到 1 分钟之间")
    public boolean isPluginDetectionTimeoutValid() {
        return pluginDetectionTimeout != null
                && pluginDetectionTimeout.compareTo(Duration.ofSeconds(3)) >= 0
                && pluginDetectionTimeout.compareTo(Duration.ofSeconds(60)) <= 0;
    }

    @AssertTrue(message = "OCS 登录二维码超时必须在 30 秒到 5 分钟之间")
    public boolean isLoginQrTimeoutValid() {
        return loginQrTimeout != null
                && loginQrTimeout.compareTo(Duration.ofSeconds(30)) >= 0
                && loginQrTimeout.compareTo(Duration.ofMinutes(5)) <= 0;
    }

    private static List<String> copy(List<String> valueList) {
        return valueList == null ? List.of() : List.copyOf(valueList);
    }

}
