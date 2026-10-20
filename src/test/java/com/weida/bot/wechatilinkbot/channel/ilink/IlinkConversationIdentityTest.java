package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.junit.jupiter.api.Test;

class IlinkConversationIdentityTest {

    private final IlinkConversationIdentity identity = new IlinkConversationIdentity();

    @Test
    void isolatesTheSameWechatUserAcrossBotTenants() {
        WeixinMessage message = message("stable-user@im.wechat");

        String firstConversationId = identity.conversationId(
                client("first-bot@im.bot"), message);
        String secondConversationId = identity.conversationId(
                client("second-bot@im.bot"), message);

        assertThat(firstConversationId)
                .isEqualTo("wechat-ilink|bot=first-bot@im.bot|user=stable-user@im.wechat");
        assertThat(secondConversationId)
                .isEqualTo("wechat-ilink|bot=second-bot@im.bot|user=stable-user@im.wechat")
                .isNotEqualTo(firstConversationId);
    }

    @Test
    void separatesDifferentWechatUsers() {
        ILinkClient client = client("bot@im.bot");

        assertThat(identity.conversationId(client, message("user-a@im.wechat")))
                .isNotEqualTo(identity.conversationId(
                        client, message("user-b@im.wechat")));
    }

    private static WeixinMessage message(String sender) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(1L);
        message.setFrom_user_id(sender);
        return message;
    }

    private static ILinkClient client(String botId) {
        ILinkClient client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(new LoginContext(
                "token",
                "owner@im.wechat",
                botId,
                "https://example.test"));
        return client;
    }
}
