package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounds for extracting untrusted document content.
 */
@Validated
@ConfigurationProperties(prefix = "bot.capability.document")
public class DocumentProperties {

    @Min(1024)
    private int maxCharacters = 30_000;

    @Min(1024)
    @Max(52_428_800)
    private int maxBytes = 10 * 1024 * 1024;

    public int getMaxCharacters() {
        return maxCharacters;
    }

    public void setMaxCharacters(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }
}
