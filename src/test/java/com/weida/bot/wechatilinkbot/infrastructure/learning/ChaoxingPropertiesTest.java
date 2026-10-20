package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ChaoxingPropertiesTest {

    @Test
    void rejectsUnsafeQrChallengeTimeouts() {
        ChaoxingProperties properties = new ChaoxingProperties();

        assertThatThrownBy(() -> properties.setLoginChallengeTimeout(Duration.ofSeconds(29)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setLoginChallengeTimeout(Duration.ofMinutes(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
