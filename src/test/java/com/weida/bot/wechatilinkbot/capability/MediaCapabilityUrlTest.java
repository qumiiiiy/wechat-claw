package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaClient;
import com.weida.bot.wechatilinkbot.infrastructure.media.DashScopeMediaProperties;
import com.weida.bot.wechatilinkbot.infrastructure.media.FfmpegMediaConverter;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaCapabilityUrlTest {

    private MediaCapability capability;

    @BeforeEach
    void setUp() {
        DashScopeMediaProperties properties = new DashScopeMediaProperties();
        properties.setEnabled(false);
        capability = new MediaCapability(
                properties,
                mock(FfmpegMediaConverter.class),
                mock(DashScopeMediaClient.class));
    }

    @Test
    void upgradesTrustedAliyunMediaUrlToHttpsWithoutChangingSignature() throws IOException {
        URI mediaUri = URI.create(
                "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/pre/audio.wav"
                        + "?Expires=123&Signature=a%2Fb%3D");

        URI normalizedUri = capability.normalizeTrustedDownloadUri(mediaUri);

        assertThat(normalizedUri.toASCIIString()).isEqualTo(
                "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/pre/audio.wav"
                        + "?Expires=123&Signature=a%2Fb%3D");
    }

    @Test
    void rejectsUntrustedHttpMediaUrl() {
        URI mediaUri = URI.create("http://aliyuncs.com.attacker.example/audio.wav");

        assertThatThrownBy(() -> capability.normalizeTrustedDownloadUri(mediaUri))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("host=aliyuncs.com.attacker.example");
    }

    @Test
    void preservesTrustedHttpsMediaUrl() throws IOException {
        URI mediaUri = URI.create(
                "https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/audio.wav");

        assertThat(capability.normalizeTrustedDownloadUri(mediaUri)).isEqualTo(mediaUri);
    }
}
