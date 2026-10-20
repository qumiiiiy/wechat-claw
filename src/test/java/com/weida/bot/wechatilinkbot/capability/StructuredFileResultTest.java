package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StructuredFileResultTest {

    @Test
    void rejectsUnsafeRelativeDirectory() {
        assertThatThrownBy(() -> new StructuredFileResult(
                1,
                "report.pdf",
                "..\\private",
                10,
                "10 B",
                Instant.now(),
                "file-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相对目录");
    }
}
