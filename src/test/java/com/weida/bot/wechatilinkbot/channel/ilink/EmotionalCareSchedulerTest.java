package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmotionalCareSchedulerTest {

    @Test
    void queuesCareContentWithoutAWeChatSessionOrClient() {
        IlinkPendingReplyStore store = new IlinkPendingReplyStore(new IlinkProperties());
        EmotionalProfileService profiles = mock(EmotionalProfileService.class);
        when(profiles.getProfileSnapshot()).thenReturn(Map.of(
                "wechat-ilink|synthetic-user",
                "用户最近状态：最近有点焦虑\n[MOOD: low]"));
        EmotionalCareScheduler scheduler = new EmotionalCareScheduler(store, profiles);

        scheduler.checkOnUsers();

        assertThat(store.consume("wechat-ilink|synthetic-user"))
                .hasValueSatisfying(content -> assertThat(content).contains("想聊聊吗"));
    }

    @Test
    void ignoresNonIlinkAndNonNegativeProfiles() {
        IlinkPendingReplyStore store = new IlinkPendingReplyStore(new IlinkProperties());
        EmotionalProfileService profiles = mock(EmotionalProfileService.class);
        when(profiles.getProfileSnapshot()).thenReturn(Map.of(
                "web|synthetic-user", "用户最近状态：很焦虑\n[MOOD: low]",
                "wechat-ilink|happy-user", "用户最近状态：今天很开心\n[MOOD: positive]",
                "wechat-ilink|legacy-user", "用户最近状态：很焦虑"));
        EmotionalCareScheduler scheduler = new EmotionalCareScheduler(store, profiles);

        scheduler.checkOnUsers();

        assertThat(store.size()).isZero();
    }

    @Test
    void keepsOnlyOnePendingCareReplyPerConversation() {
        IlinkPendingReplyStore store = new IlinkPendingReplyStore(new IlinkProperties());
        EmotionalProfileService profiles = mock(EmotionalProfileService.class);
        when(profiles.getProfileSnapshot()).thenReturn(Map.of(
                "wechat-ilink|synthetic-user",
                "用户最近状态：最近有点难过\n[MOOD: low]"));
        EmotionalCareScheduler scheduler = new EmotionalCareScheduler(store, profiles);

        scheduler.checkOnUsers();
        scheduler.checkOnUsers();

        assertThat(store.size()).isEqualTo(1);
    }
}
