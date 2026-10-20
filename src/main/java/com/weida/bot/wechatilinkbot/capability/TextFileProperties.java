package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for the opt-in text-file generation tool.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Validated
@ConfigurationProperties(prefix = "bot.capability.text-file")
public class TextFileProperties {

    private boolean enabled;

    @Min(1)
    @Max(200)
    private int maxFileNameCharacters = 100;

    @Min(1)
    @Max(200000)
    private int maxContentCharacters = 50000;

    @Min(1024)
    @Max(2097152)
    private int maxContentBytes = 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxFileNameCharacters() {
        return maxFileNameCharacters;
    }

    public void setMaxFileNameCharacters(int maxFileNameCharacters) {
        this.maxFileNameCharacters = maxFileNameCharacters;
    }

    public int getMaxContentCharacters() {
        return maxContentCharacters;
    }

    public void setMaxContentCharacters(int maxContentCharacters) {
        this.maxContentCharacters = maxContentCharacters;
    }

    public int getMaxContentBytes() {
        return maxContentBytes;
    }

    public void setMaxContentBytes(int maxContentBytes) {
        this.maxContentBytes = maxContentBytes;
    }
}
