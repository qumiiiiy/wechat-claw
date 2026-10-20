package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.agent.AgentRuntime;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IlinkMessageHandlerTest {

    private ILinkClient client;
    private IlinkInboundNormalizer normalizer;
    private AgentRuntime agentRuntime;
    private IlinkReplySender replySender;
    private IlinkPendingReplyStore pendingReplyStore;
    private IlinkMessageHandler handler;

    @BeforeEach
    void setUp() {
        client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(new LoginContext(
                "token",
                "owner@im.wechat",
                "bot@im.bot",
                "https://example.test"));
        normalizer = mock(IlinkInboundNormalizer.class);
        agentRuntime = mock(AgentRuntime.class);
        when(agentRuntime.maxMediaBytes()).thenReturn(1024);
        replySender = mock(IlinkReplySender.class);
        IlinkProperties properties = new IlinkProperties();
        pendingReplyStore = new IlinkPendingReplyStore(properties);
        handler = new IlinkMessageHandler(
                properties,
                normalizer,
                agentRuntime,
                new IlinkConversationIdentity(),
                new ConversationCommand(),
                replySender,
                pendingReplyStore);
    }

    @Test
    void fallsBackWithoutCallingAnUnavailableModel() {
        WeixinMessage message = textMessage("sender@im.wechat", "测试消息");
        IlinkInboundMessage inbound = new IlinkInboundMessage(
                "prompt",
                "fallback",
                "测试消息",
                List.of());
        when(normalizer.normalize(client, message, false, 1024)).thenReturn(inbound);
        when(agentRuntime.available()).thenReturn(false);

        handler.handleMessages(client, List.of(message));

        verify(agentRuntime, never()).reply(any(AgentRequest.class));
        verify(replySender).send(
                any(IlinkReplyContext.class),
                eq("fallback"),
                eq(new AgentReply("fallback", List.of())));
    }

    @Test
    void passesOnlyTheStableAgentRequestAcrossTheChannelBoundary() {
        WeixinMessage message = textMessage("sender@im.wechat", "测试消息");
        IlinkInboundMessage inbound = new IlinkInboundMessage(
                "normalized prompt",
                "fallback",
                "测试消息",
                List.of());
        when(agentRuntime.available()).thenReturn(true);
        when(normalizer.normalize(client, message, true, 1024)).thenReturn(inbound);
        when(agentRuntime.reply(any(AgentRequest.class)))
                .thenReturn(Optional.of(new AgentReply("answer", List.of())));

        handler.handleMessages(client, List.of(message));

        ArgumentCaptor<AgentRequest> requestCaptor =
                ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentRuntime).reply(requestCaptor.capture());
        assertThat(requestCaptor.getValue().conversationId())
                .isEqualTo("wechat-ilink|bot=bot@im.bot|user=sender@im.wechat");
        assertThat(requestCaptor.getValue().prompt()).isEqualTo("normalized prompt");
        assertThat(requestCaptor.getValue().currentUserText()).isEqualTo("测试消息");
    }

    @Test
    void clearCommandClearsMemoryWithoutRunningTheModel() {
        WeixinMessage message = textMessage("sender@im.wechat", "/clear");

        handler.handleMessages(client, List.of(message));

        verify(agentRuntime).clearConversation(
                "wechat-ilink|bot=bot@im.bot|user=sender@im.wechat");
        verify(agentRuntime, never()).reply(any(AgentRequest.class));
        verify(replySender).sendText(
                any(IlinkReplyContext.class),
                any(String.class),
                any(String.class));
    }

    @Test
    void ignoresMessagesSentByTheBot() {
        WeixinMessage message = textMessage("bot@im.bot", "self message");
        message.setMessage_type(2);

        handler.handleMessages(client, List.of(message));

        verify(normalizer, never()).normalize(any(), any(), anyBoolean(), anyInt());
    }

    @Test
    void rejectsReplyWhenInboundMessageHasNoContextToken() {
        WeixinMessage message = textMessage("sender@im.wechat", "测试消息");
        message.setContext_token(null);

        handler.handleMessages(client, List.of(message));

        verify(normalizer, never()).normalize(any(), any(), anyBoolean(), anyInt());
        verify(replySender, never()).send(
                any(IlinkReplyContext.class), anyString(), any(AgentReply.class));
    }

    @Test
    void attachesQueuedContentOnlyInsideNextValidReplyWindow() {
        WeixinMessage message = textMessage("sender@im.wechat", "测试消息");
        IlinkInboundMessage inbound = new IlinkInboundMessage(
                "prompt",
                "fallback",
                "测试消息",
                List.of());
        when(normalizer.normalize(client, message, false, 1024)).thenReturn(inbound);
        when(agentRuntime.available()).thenReturn(false);
        pendingReplyStore.queue(
                "wechat-ilink|bot=bot@im.bot|user=sender@im.wechat",
                "待回复内容");

        handler.handleMessages(client, List.of(message));

        ArgumentCaptor<AgentReply> replyCaptor = ArgumentCaptor.forClass(AgentReply.class);
        verify(replySender).send(
                any(IlinkReplyContext.class),
                eq("fallback"),
                replyCaptor.capture());
        assertThat(replyCaptor.getValue().text()).contains("fallback", "待回复内容");
        assertThat(pendingReplyStore.size()).isZero();
    }

    private static WeixinMessage textMessage(String sender, String text) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setMessage_type(1);
        message.setFrom_user_id(sender);
        message.setTo_user_id("bot@im.bot");
        message.setCreate_time_ms(System.currentTimeMillis());
        message.setContext_token("reply-context-token");
        message.setItem_list(List.of(MessageItem.text(text)));
        return message;
    }
}
