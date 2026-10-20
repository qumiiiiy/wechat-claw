package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.junit.jupiter.api.Test;

class IlinkReplyContextTest {

    @Test
    void rejectsMissingContextToken() {
        ILinkClient client = client("bot");
        WeixinMessage message = message("user", "bot", null);

        assertThatThrownBy(() -> IlinkReplyContext.open(client, message, "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("context token");
    }

    @Test
    void rejectsBotMismatch() {
        ILinkClient client = client("other-bot");
        WeixinMessage message = message("user", "bot", "reply-token");

        assertThatThrownBy(() -> IlinkReplyContext.open(client, message, "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bot 不匹配");
    }

    @Test
    void rejectsTargetMismatch() {
        ILinkClient client = client("bot");
        WeixinMessage message = message("user-a", "bot", "reply-token");

        assertThatThrownBy(() -> IlinkReplyContext.open(client, message, "user-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("目标");
    }

    @Test
    void rejectsReuseAfterReplyWindowCloses() {
        ILinkClient client = client("bot");
        IlinkReplyContext context = IlinkReplyContext.open(
                client,
                message("user", "bot", "reply-token"),
                "user");

        context.close();

        assertThatThrownBy(context::client)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("失效");
    }

    @Test
    void rejectsContextAfterClientChangesBot() {
        ILinkClient client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(login("bot"), login("other-bot"));
        IlinkReplyContext context = IlinkReplyContext.open(
                client,
                message("user", "bot", "reply-token"),
                "user");

        assertThatThrownBy(context::client)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Bot 已发生变化");
    }

    private static ILinkClient client(String botId) {
        ILinkClient client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(login(botId));
        return client;
    }

    private static LoginContext login(String botId) {
        return new LoginContext("login-token", "owner", botId, "https://example.test");
    }

    private static WeixinMessage message(String from, String to, String contextToken) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setFrom_user_id(from);
        message.setTo_user_id(to);
        message.setContext_token(contextToken);
        return message;
    }
}
