package com.weida.bot.wechatilinkbot.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retention and optional SQLite settings for conversation memory.
 */
@Validated
@ConfigurationProperties(prefix = "bot.persistence.conversation")
public class ConversationMemoryProperties {

    @Pattern(regexp = "memory|sqlite|mysql", message = "存储类型只能是 memory、sqlite 或 mysql")
    private String type = "sqlite";

    @Min(2)
    @Max(1000)
    private int retainedMessages = 100;

    @Min(1024)
    @Max(1_000_000)
    private int maxMessageCharacters = 50_000;

    @NotNull
    private Duration expireAfter = Duration.ZERO;

    private final Sqlite sqlite = new Sqlite();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getRetainedMessages() {
        return retainedMessages;
    }

    public void setRetainedMessages(int retainedMessages) {
        this.retainedMessages = retainedMessages;
    }

    public int getMaxMessageCharacters() {
        return maxMessageCharacters;
    }

    public void setMaxMessageCharacters(int maxMessageCharacters) {
        this.maxMessageCharacters = maxMessageCharacters;
    }

    public Duration getExpireAfter() {
        return expireAfter;
    }

    public void setExpireAfter(Duration expireAfter) {
        this.expireAfter = expireAfter;
    }

    public boolean expirationEnabled() {
        return expireAfter != null && !expireAfter.isZero();
    }

    @AssertTrue(message = "会话记忆过期时间不能为负数")
    public boolean isExpireAfterValid() {
        return expireAfter != null && !expireAfter.isNegative();
    }

    public Sqlite getSqlite() {
        return sqlite;
    }

    public static class Sqlite {

        @Pattern(
                regexp = "jdbc:sqlite:(?!$).+",
                message = "SQLite JDBC URL 必须以 jdbc:sqlite: 开头")
        private String jdbcUrl = "jdbc:sqlite:./data/wechat-bot.db";

        @Min(100)
        @Max(60_000)
        private int busyTimeoutMillis = 5_000;

        @NotNull
        private Duration connectionTimeout = Duration.ofSeconds(10);

        @NotNull
        private Duration validationTimeout = Duration.ofSeconds(5);

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public int getBusyTimeoutMillis() {
            return busyTimeoutMillis;
        }

        public void setBusyTimeoutMillis(int busyTimeoutMillis) {
            this.busyTimeoutMillis = busyTimeoutMillis;
        }

        public Duration getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Duration getValidationTimeout() {
            return validationTimeout;
        }

        public void setValidationTimeout(Duration validationTimeout) {
            this.validationTimeout = validationTimeout;
        }
    }
}
