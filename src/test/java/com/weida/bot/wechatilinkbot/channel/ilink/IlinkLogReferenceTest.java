package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IlinkLogReferenceTest {

    @Test
    void createsStableReferenceWithoutExposingRawIdentity() {
        String rawIdentity = "synthetic-user-id@im.wechat";

        String reference = IlinkLogReference.of(rawIdentity);

        assertThat(reference)
                .isEqualTo(IlinkLogReference.of(rawIdentity))
                .doesNotContain(rawIdentity)
                .hasSize(12);
    }

    @Test
    void handlesMissingIdentityWithoutLoggingNull() {
        assertThat(IlinkLogReference.of(null)).isEqualTo("none");
        assertThat(IlinkLogReference.of(" ")).isEqualTo("none");
    }
}
