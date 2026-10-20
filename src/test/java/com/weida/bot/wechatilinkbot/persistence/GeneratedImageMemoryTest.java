package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.model.MediaPart;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeneratedImageMemoryTest {

    @Test
    void boundsTotalBytesAndExpiresRememberedImages() {
        GeneratedImageMemoryProperties properties = new GeneratedImageMemoryProperties();
        properties.setMaxImageBytes(4);
        properties.setMaxTotalBytes(4);
        properties.setExpireAfter(Duration.ofMinutes(30));
        AtomicReference<Instant> currentTime = new AtomicReference<>(
                Instant.parse("2026-07-21T00:00:00Z"));
        GeneratedImageMemory memory = new GeneratedImageMemory(properties) {
            @Override
            Instant currentTime() {
                return currentTime.get();
            }
        };

        memory.remember("user-a", List.of(image(new byte[] {1, 2, 3}, "first.png")));
        MediaPart first = memory.getLatest("user-a").orElseThrow();
        assertThat(first.bytes()).containsExactly(1, 2, 3);

        memory.remember("user-b", List.of(image(new byte[] {4, 5, 6}, "second.jpg")));
        assertThat(memory.getLatest("user-a")).isEmpty();
        assertThat(memory.getLatest("user-b").orElseThrow().mimeType())
                .isEqualTo("image/jpeg");

        currentTime.set(currentTime.get().plus(Duration.ofHours(1)));
        assertThat(memory.getLatest("user-b")).isEmpty();
    }

    private static GeneratedMedia image(byte[] bytes, String fileName) {
        return new GeneratedMedia(
                GeneratedMediaKind.IMAGE, bytes, fileName, 0L, "");
    }
}
