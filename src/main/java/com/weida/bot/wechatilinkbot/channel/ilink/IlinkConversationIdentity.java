package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import org.springframework.stereotype.Component;

/** Resolves a Bot-scoped conversation identity for the WeChat channel. */
@Component
public class IlinkConversationIdentity {

    private static final String CHANNEL_PREFIX = "wechat-ilink|";
    private static final String UNKNOWN_BOT_ID = "unknown-bot";

    /**
     * Returns the current Bot account identifier for the tenant boundary.
     */
    public String botId(ILinkClient client) {
        LoginContext loginContext = client == null ? null : client.getLoginContext();
        String botId = loginContext == null ? null : loginContext.getBotId();
        return botId == null || botId.isBlank() ? UNKNOWN_BOT_ID : botId.strip();
    }

    /**
     * Combines the Bot account and user identifier so two Bot tenants cannot
     * share history, pending work, profiles, or capability state.
     *
     * @param client active WeChat client
     * @param message inbound message
     * @return Bot-scoped channel conversation identifier
     */
    public String conversationId(ILinkClient client, WeixinMessage message) {
        String bot = component(botId(client));
        String sender = message == null ? null : message.getFrom_user_id();
        if (sender != null && !sender.isBlank()) {
            return key(bot, sender);
        }
        LoginContext loginContext = client == null ? null : client.getLoginContext();
        String loginUserId = loginContext == null ? null : loginContext.getUserId();
        if (loginUserId != null && !loginUserId.isBlank()) {
            return key(bot, loginUserId);
        }
        Long messageId = message == null ? null : message.getMessage_id();
        return CHANNEL_PREFIX + "bot=" + bot + "|user=unknown-message-" + messageId;
    }

    private static String key(String bot, String user) {
        return CHANNEL_PREFIX + "bot=" + bot + "|user=" + component(user);
    }

    private static String component(String value) {
        return value.strip()
                .replace("%", "%25")
                .replace("|", "%7C")
                .replace("\r", "%0D")
                .replace("\n", "%0A");
    }
}
