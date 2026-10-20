package com.weida.bot.wechatilinkbot.agent.intent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesktopTaskIntentDetectorTest {

    private final DesktopTaskIntentDetector detector = new DesktopTaskIntentDetector();

    @Test
    void detectsEachExplicitDesktopTaskOperation() {
        assertThat(detector.detect("打开 https://example.edu.cn/course"))
                .isEqualTo(DesktopTaskOperation.REQUEST_OPEN_URL);
        assertThat(detector.detect("帮我在电脑上打开我的 QQ 邮箱"))
                .isEqualTo(DesktopTaskOperation.REQUEST_OPEN_URL);
        assertThat(detector.detect("确认 DT-12345678-abcd"))
                .isEqualTo(DesktopTaskOperation.CONFIRM);
        assertThat(detector.detect("查询任务 DT-12345678-abcd 状态"))
                .isEqualTo(DesktopTaskOperation.STATUS);
        assertThat(detector.detect("取消 DT-12345678-abcd"))
                .isEqualTo(DesktopTaskOperation.CANCEL);
    }

    @Test
    void doesNotExposeDesktopToolsForGenericConversation() {
        assertThat(detector.detect("帮我打开学校官网"))
                .isEqualTo(DesktopTaskOperation.NONE);
        assertThat(detector.detect("打开百度"))
                .isEqualTo(DesktopTaskOperation.NONE);
    }
}
