package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QrCodeIntentDetectorTest {

    private final QrCodeIntentDetector detector = new QrCodeIntentDetector();

    @Test
    void recognizesExplicitQrCodeGenerationRequest() {
        assertThat(detector.hasIntent("帮我生成一个文本二维码")).isTrue();
        assertThat(detector.hasIntent("给我做个二维码")).isTrue();
        assertThat(detector.hasIntent("来一个QR码")).isTrue();
        assertThat(detector.hasIntent("make a qrcode for https://example.com")).isTrue();
        assertThat(detector.hasIntent("二维码内容：hello world")).isTrue();
        assertThat(detector.hasIntent("二维码:https://example.com")).isTrue();
    }

    @Test
    void doesNotConfuseGenericQrMentionWithGeneration() {
        assertThat(detector.hasIntent("二维码是什么")).isFalse();
        assertThat(detector.hasIntent("二维码和条形码的区别")).isFalse();
        assertThat(detector.hasIntent("今天扫了个qr码")).isFalse();
        assertThat(detector.hasIntent("打开百度")).isFalse();
    }

    @Test
    void honorsExplicitNegation() {
        assertThat(detector.hasIntent("不要二维码")).isFalse();
        assertThat(detector.hasIntent("不用qrcode")).isFalse();
        assertThat(detector.hasIntent("别给我做二维码")).isFalse();
    }

    @Test
    void recognizesRetryFromTheImmediatelyPrecedingQrCodeRequest() {
        Instant now = Instant.now();
        List<ConversationMessage> history = List.of(
                new ConversationMessage(
                        1L,
                        "conversation",
                        "user",
                        "帮我生成一个二维码，内容是 hello",
                        now,
                        now),
                new ConversationMessage(
                        2L,
                        "conversation",
                        "assistant",
                        "已经生成。",
                        now,
                        now));

        assertThat(detector.hasContinuationIntent(history, "再试一次")).isTrue();
    }

    @Test
    void handlesNullAndBlankInput() {
        assertThat(detector.hasIntent(null)).isFalse();
        assertThat(detector.hasIntent("")).isFalse();
        assertThat(detector.hasIntent("   ")).isFalse();
    }
}
