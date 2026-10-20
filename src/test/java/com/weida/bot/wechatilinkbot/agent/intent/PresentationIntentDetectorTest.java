package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PresentationIntentDetectorTest {

    private final PresentationIntentDetector detector = new PresentationIntentDetector();

    @Test
    void recognizesExplicitPresentationRequests() {
        assertThat(detector.hasIntent("请做一个关于项目复盘的 PPTX")).isTrue();
        assertThat(detector.hasIntent("生成一份课程总结演示文稿")).isTrue();
    }

    @Test
    void doesNotActivateForUnrelatedConversation() {
        assertThat(detector.hasIntent("帮我总结一下这段文字")).isFalse();
    }
}
