package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IlinkPendingReplyStoreTest {

    @Test
    void enforcesEntryAndCharacterBounds() {
        IlinkProperties properties = new IlinkProperties();
        properties.setMaxPendingReplies(1);
        properties.setMaxPendingReplyCharacters(4);
        IlinkPendingReplyStore store = new IlinkPendingReplyStore(properties);

        assertThat(store.queue("wechat-ilink|a", "123456")).isTrue();
        assertThat(store.queue("wechat-ilink|b", "second")).isFalse();
        assertThat(store.consume("wechat-ilink|a")).contains("1234");
    }
}
