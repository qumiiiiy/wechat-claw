package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.agent.AgentRuntime;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin inbound iLink orchestrator: validate, normalize, run the agent, and deliver.
 */
@Component
public class IlinkMessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkMessageHandler.class);
    private static final String RATE_LIMIT_NOTICE = "消息有点密集，请稍后再试。";
    private static final String MEMORY_CLEARED_NOTICE = "对话记忆已清空，接下来我们重新开始。";

    private final IlinkProperties properties;
    private final IlinkInboundNormalizer inboundNormalizer;
    private final AgentRuntime agentRuntime;
    private final IlinkConversationIdentity conversationIdentity;
    private final ConversationCommand conversationCommand;
    private final IlinkReplySender replySender;
    private final IlinkPendingReplyStore pendingReplyStore;

    public IlinkMessageHandler(
            IlinkProperties properties,
            IlinkInboundNormalizer inboundNormalizer,
            AgentRuntime agentRuntime,
            IlinkConversationIdentity conversationIdentity,
            ConversationCommand conversationCommand,
            IlinkReplySender replySender,
            IlinkPendingReplyStore pendingReplyStore) {
        this.properties = properties;
        this.inboundNormalizer = inboundNormalizer;
        this.agentRuntime = agentRuntime;
        this.conversationIdentity = conversationIdentity;
        this.conversationCommand = conversationCommand;
        this.replySender = replySender;
        this.pendingReplyStore = pendingReplyStore;
    }

    public void handleMessages(ILinkClient client, List<WeixinMessage> messageList) {
        if (client == null || messageList == null || messageList.isEmpty()) {
            return;
        }
        for (WeixinMessage message : messageList) {
            try {
                handleMessage(client, message);
            } catch (RuntimeException exception) {
                LOG.error(
                        "wechat_message_handle_failed messageRef={}",
                        IlinkLogReference.of(message == null ? null : message.getMessage_id()),
                        exception);
                // The dispatcher owns the bounded retry policy. Do not turn a
                // processing failure into an apparent success here.
                throw exception;
            }
        }
    }

    public void handleRateLimitedMessage(ILinkClient client, WeixinMessage message) {
        if (client == null || message == null || invalidSender(message)) {
            return;
        }
        LOG.warn(
                "wechat_message_rate_limited sender={} messageRef={}",
                mask(message.getFrom_user_id()),
                IlinkLogReference.of(message.getMessage_id()));
        try (IlinkReplyContext replyContext = openReplyContext(
                client, message, message.getFrom_user_id())) {
            if (replyContext != null) {
                replySender.sendText(replyContext, RATE_LIMIT_NOTICE, RATE_LIMIT_NOTICE);
            }
        }
    }

    private void handleMessage(ILinkClient client, WeixinMessage message) {
        if (message == null || invalidSender(message) || isMessageFromBot(client, message)) {
            return;
        }
        List<MessageItem> itemList = message.getItem_list();
        if (itemList == null || itemList.isEmpty()) {
            LOG.info(
                    "wechat_empty_message_received messageRef={}",
                    IlinkLogReference.of(message.getMessage_id()));
            return;
        }
        String sender = message.getFrom_user_id();
        try (IlinkReplyContext replyContext = openReplyContext(client, message, sender)) {
            if (replyContext == null) {
                return;
            }
            String conversationId = conversationIdentity.conversationId(client, message);
            if (conversationCommand.isClearCommand(itemList)) {
                agentRuntime.clearConversation(conversationId);
                replySender.sendText(
                        replyContext,
                        MEMORY_CLEARED_NOTICE,
                        MEMORY_CLEARED_NOTICE);
                return;
            }

            boolean agentAvailable = properties.isAutoReplyEnabled() && agentRuntime.available();
            IlinkInboundMessage inbound = inboundNormalizer.normalize(
                    client,
                    message,
                    agentAvailable,
                    agentRuntime.maxMediaBytes());
            if (!properties.isAutoReplyEnabled()
                    || (inbound.prompt().isBlank() && inbound.fallback().isBlank())) {
                return;
            }

            AgentReply reply = requestAgentReply(conversationId, inbound, replyContext)
                    .orElseGet(() -> new AgentReply(inbound.fallback(), List.of()));
            AgentReply replyWithPending = attachPendingReply(conversationId, reply);
            replySender.send(replyContext, inbound.fallback(), replyWithPending);
        }
    }

    private Optional<AgentReply> requestAgentReply(
            String conversationId,
            IlinkInboundMessage inbound,
            IlinkReplyContext replyContext) {
        if (!agentRuntime.available()) {
            return Optional.empty();
        }
        try {
            AgentRequest request = new AgentRequest(
                    conversationId,
                    inbound.prompt(),
                    inbound.currentUserText(),
                    inbound.mediaParts());
            LOG.info(
                    "agent_reply_requested target={} sourceMessageRef={} mediaParts={}",
                    mask(replyContext.targetUserId()),
                    IlinkLogReference.of(replyContext.sourceMessageId()),
                    inbound.mediaParts().size());
            return agentRuntime.reply(request);
        } catch (RuntimeException exception) {
            LOG.error(
                    "agent_reply_failed target={} sourceMessageRef={} fallback=true",
                    mask(replyContext.targetUserId()),
                    IlinkLogReference.of(replyContext.sourceMessageId()),
                    exception);
            return Optional.empty();
        }
    }

    private AgentReply attachPendingReply(String conversationId, AgentReply reply) {
        return pendingReplyStore.consume(conversationId)
                .map(pending -> new AgentReply(
                        reply.text().isBlank() ? pending : reply.text() + "\n\n" + pending,
                        reply.generatedMedia()))
                .orElse(reply);
    }

    private IlinkReplyContext openReplyContext(
            ILinkClient client,
            WeixinMessage message,
            String target) {
        try {
            return IlinkReplyContext.open(client, message, target);
        } catch (IllegalStateException exception) {
            LOG.warn(
                    "wechat_reply_context_rejected messageRef={} reason={}",
                    IlinkLogReference.of(message == null ? null : message.getMessage_id()),
                    exception.getMessage());
            return null;
        }
    }

    private static boolean invalidSender(WeixinMessage message) {
        return message.getFrom_user_id() == null || message.getFrom_user_id().isBlank();
    }

    private static boolean isMessageFromBot(ILinkClient client, WeixinMessage message) {
        if (Integer.valueOf(2).equals(message.getMessage_type())) {
            return true;
        }
        LoginContext loginContext = client.getLoginContext();
        return loginContext != null
                && message.getFrom_user_id().equals(loginContext.getBotId());
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }
}
