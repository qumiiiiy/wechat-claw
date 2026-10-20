package com.weida.bot.wechatilinkbot.capability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded emotional-profile summary memory for long-term continuity.
 *
 * @author ryx
 * @since 2026-07-23
 */
@Validated
@ConfigurationProperties(prefix = "bot.capability.profile")
public class ConversationProfileProperties {

    private boolean enabled = true;

    @NotNull
    private Duration ttl = Duration.ofDays(30);

    @Min(64)
    @Max(16_384)
    private int maxProfileCharacters = 2000;

    @Min(1)
    @Max(1000)
    private int maxProfiles = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public int getMaxProfileCharacters() {
        return maxProfileCharacters;
    }

    public void setMaxProfileCharacters(int maxProfileCharacters) {
        this.maxProfileCharacters = maxProfileCharacters;
    }

    public int getMaxProfiles() {
        return maxProfiles;
    }

    public void setMaxProfiles(int maxProfiles) {
        this.maxProfiles = maxProfiles;
    }
}
