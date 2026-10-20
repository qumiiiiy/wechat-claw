package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory authority for replies to one trusted inbound iLink message.
 *
 * <p>The context token deliberately has no accessor and is cleared when the
 * reply window closes. This type must stay inside the iLink adapter.</p>
 */
final class IlinkReplyContext implements AutoCloseable {

    private final ILinkClient client;
    private final String botId;
    private final long sourceMessageId;
    private final String targetUserId;
    private final AtomicBoolean open = new AtomicBoolean(true);
    private String contextToken;

    private IlinkReplyContext(
            ILinkClient client,
            String botId,
            long sourceMessageId,
            String targetUserId,
            String contextToken) {
        this.client = client;
        this.botId = botId;
        this.sourceMessageId = sourceMessageId;
        this.targetUserId = targetUserId;
        this.contextToken = contextToken;
    }

    static IlinkReplyContext open(
            ILinkClient client,
            WeixinMessage sourceMessage,
            String targetUserId) {
        if (client == null || sourceMessage == null) {
            throw new IllegalStateException("缺少可信入站消息上下文");
        }
        String target = requireText(targetUserId, "目标用户");
        if (!target.equals(sourceMessage.getFrom_user_id())) {
            throw new IllegalStateException("回复目标与源消息发送者不匹配");
        }
        Long messageId = sourceMessage.getMessage_id();
        if (messageId == null) {
            throw new IllegalStateException("源消息缺少消息 ID");
        }
        String token = requireText(sourceMessage.getContext_token(), "消息 context token");
        LoginContext loginContext = client.getLoginContext();
        if (loginContext == null) {
            throw new IllegalStateException("当前 SDK Client 未绑定登录 Bot");
        }
        String botId = requireText(loginContext.getBotId(), "Bot ID");
        if (!botId.equals(sourceMessage.getTo_user_id())) {
            throw new IllegalStateException("源消息与当前 Bot 不匹配");
        }
        return new IlinkReplyContext(client, botId, messageId, target, token);
    }

    ILinkClient client() {
        requireActive();
        return client;
    }

    String targetUserId() {
        requireActive();
        return targetUserId;
    }

    long sourceMessageId() {
        requireActive();
        return sourceMessageId;
    }

    private void requireActive() {
        if (!open.get() || contextToken == null) {
            throw new IllegalStateException("回复上下文已经失效");
        }
        LoginContext currentLogin = client.getLoginContext();
        if (currentLogin == null || !botId.equals(currentLogin.getBotId())) {
            throw new IllegalStateException("回复上下文的 Bot 已发生变化");
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            contextToken = null;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " 不能为空");
        }
        return value.strip();
    }
}
