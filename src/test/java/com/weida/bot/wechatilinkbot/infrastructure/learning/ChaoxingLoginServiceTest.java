package com.weida.bot.wechatilinkbot.infrastructure.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChaoxingLoginServiceTest {

    private final ChaoxingProperties properties = new ChaoxingProperties();
    private final ChaoxingClient chaoxingClient = mock(ChaoxingClient.class);
    private ChaoxingLoginService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        service = new ChaoxingLoginService(properties, chaoxingClient);
    }

    @Test
    void returnsQrChallengeFromSharedLearningSession() {
        ChaoxingLoginChallenge challenge = new ChaoxingLoginChallenge(
                new byte[]{1, 2, 3}, Instant.parse("2026-08-04T12:03:00Z"));
        when(chaoxingClient.startQrLogin()).thenReturn(challenge);

        assertThat(service.login()).isEqualTo(challenge);
    }

    @Test
    void propagatesStableUpstreamFailure() {
        when(chaoxingClient.startQrLogin())
                .thenThrow(new ChaoxingLoginException("合成登录页缺少二维码"));

        assertThatThrownBy(service::login)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessage("合成登录页缺少二维码");
    }

    @Test
    void disabledCapabilityDoesNotStartLogin() {
        properties.setEnabled(false);

        assertThatThrownBy(service::login)
                .isInstanceOf(ChaoxingLoginException.class)
                .hasMessageContaining("未启用");
        verify(chaoxingClient, never()).startQrLogin();
    }
}
