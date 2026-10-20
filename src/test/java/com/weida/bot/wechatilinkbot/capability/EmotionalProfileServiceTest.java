package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmotionalProfileServiceTest {

    @Test
    void stripsReplyMarkerAndRetainsProfileForLaterPrompts() {
        ConversationProfileProperties properties = new ConversationProfileProperties();
        EmotionalProfileService service = new EmotionalProfileService(properties);

        String visible = service.extractAndUpdateProfile(
                "wechat-ilink|user-1",
                "我会陪你一起处理。\n[EMOTION: 焦虑] [MOOD: low] [CONTEXT: 明天要参加答辩]");

        assertThat(visible).isEqualTo("我会陪你一起处理。");
        assertThat(service.getProfile("wechat-ilink|user-1"))
                .contains("焦虑")
                .contains("明天要参加答辩")
                .contains("[MOOD: low]");
    }

    @Test
    void disabledProfileTrackingLeavesReplyUnchanged() {
        ConversationProfileProperties properties = new ConversationProfileProperties();
        properties.setEnabled(false);
        EmotionalProfileService service = new EmotionalProfileService(properties);
        String reply = "普通回复 [EMOTION: 平静] [CONTEXT: 无]";

        assertThat(service.extractAndUpdateProfile("conversation", reply)).isEqualTo(reply);
    }
}
