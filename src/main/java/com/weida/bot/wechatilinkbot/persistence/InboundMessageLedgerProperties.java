package com.weida.bot.wechatilinkbot.persistence;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Bounds for the inbound deduplication and processing ledger. */
@Validated
@ConfigurationProperties(prefix = "bot.persistence.inbound-message")
public class InboundMessageLedgerProperties {

    @NotNull
    private Duration deduplicationTtl = Duration.ofDays(3);

    @Min(64)
    @Max(2000)
    private int maxErrorCharacters = 256;

    public Duration getDeduplicationTtl() {
        return deduplicationTtl;
    }

    public void setDeduplicationTtl(Duration deduplicationTtl) {
        this.deduplicationTtl = deduplicationTtl;
    }

    public int getMaxErrorCharacters() {
        return maxErrorCharacters;
    }

    public void setMaxErrorCharacters(int maxErrorCharacters) {
        this.maxErrorCharacters = maxErrorCharacters;
    }

    @AssertTrue(message = "入站消息去重 TTL 必须大于 0")
    public boolean isDeduplicationTtlValid() {
        return deduplicationTtl != null
                && !deduplicationTtl.isZero()
                && !deduplicationTtl.isNegative();
    }
}
