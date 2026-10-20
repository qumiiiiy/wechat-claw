package com.weida.bot.wechatilinkbot.infrastructure.learning;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学习通采集配置。
 */
@ConfigurationProperties(prefix = "bot.infrastructure.learning")
public class ChaoxingProperties {

    private static final Duration MIN_LOGIN_CHALLENGE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_LOGIN_CHALLENGE_TIMEOUT = Duration.ofMinutes(5);

    private boolean enabled = false;
    private String baseUrl = "https://mooc1.chaoxing.com";
    private String passportUrl = "https://passport2.chaoxing.com";
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration loginChallengeTimeout = Duration.ofMinutes(3);
    private String cookie;
    private String uid;
    private String enc;
    private String stuenc;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPassportUrl() {
        return passportUrl;
    }

    public void setPassportUrl(String passportUrl) {
        this.passportUrl = passportUrl;
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

    public Duration getLoginChallengeTimeout() {
        return loginChallengeTimeout;
    }

    public void setLoginChallengeTimeout(Duration loginChallengeTimeout) {
        if (loginChallengeTimeout == null
                || loginChallengeTimeout.compareTo(MIN_LOGIN_CHALLENGE_TIMEOUT) < 0
                || loginChallengeTimeout.compareTo(MAX_LOGIN_CHALLENGE_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "loginChallengeTimeout must be between 30 seconds and 5 minutes");
        }
        this.loginChallengeTimeout = loginChallengeTimeout;
    }

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEnc() {
        return enc;
    }

    public void setEnc(String enc) {
        this.enc = enc;
    }

    public String getStuenc() {
        return stuenc;
    }

    public void setStuenc(String stuenc) {
        this.stuenc = stuenc;
    }

}
