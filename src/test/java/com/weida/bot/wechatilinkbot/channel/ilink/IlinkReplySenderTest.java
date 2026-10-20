package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IlinkReplySenderTest {

    @Test
    void sendsLongRepliesAsUtf8Files() throws Exception {
        IlinkProperties properties = new IlinkProperties();
        properties.setAutoReplyFileThresholdCharacters(100);
        IlinkReplySender sender = new IlinkReplySender(properties);
        ILinkClient client = mock(ILinkClient.class);
        String text = "长".repeat(101);

        sender.send(context(client), "fallback", new AgentReply(text, List.of()));

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(client).sendFile(
                eq("user"),
                bytes.capture(),
                eq("ai-reply-123.txt"),
                eq(""));
        assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo(text);
        verify(client, never()).sendTextWithTyping(eq("user"), eq(text), eq(1_000L));
    }

    @Test
    void deliversGeneratedImageBeforeTheTextReply() throws Exception {
        IlinkReplySender sender = new IlinkReplySender(new IlinkProperties());
        ILinkClient client = mock(ILinkClient.class);
        GeneratedMedia image = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "image.png",
                0L,
                "caption");

        sender.send(
                context(client),
                "fallback",
                new AgentReply("已生成", List.of(image)));

        verify(client).sendImage(
                "user",
                image.bytes(),
                "image.png",
                "caption");
        verify(client).sendTextWithTyping("user", "已生成", 1_000L);
    }

    @Test
    void deliversGeneratedSpreadsheetThroughTheFileChannel() throws Exception {
        IlinkReplySender sender = new IlinkReplySender(new IlinkProperties());
        ILinkClient client = mock(ILinkClient.class);
        GeneratedMedia workbook = new GeneratedMedia(
                GeneratedMediaKind.FILE,
                new byte[] {1, 2, 3},
                "本周任务.xlsx",
                0L,
                "");

        sender.send(
                context(client),
                "fallback",
                new AgentReply("已生成", List.of(workbook)));

        verify(client).sendFile(
                "user",
                workbook.bytes(),
                "本周任务.xlsx",
                "");
        verify(client).sendTextWithTyping("user", "已生成", 1_000L);
    }

    @Test
    void routesImageThroughSendFileWhenDeliverImagesAsFilesEnabled() throws Exception {
        IlinkProperties properties = new IlinkProperties();
        properties.setDeliverImagesAsFiles(true);
        IlinkReplySender sender = new IlinkReplySender(properties);
        ILinkClient client = mock(ILinkClient.class);
        GeneratedMedia image = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "qwen-image-artifact.png",
                0L,
                "caption");

        sender.send(
                context(client),
                "fallback",
                new AgentReply("已生成", List.of(image)));

        verify(client).sendFile(
                "user",
                image.bytes(),
                "qwen-image-artifact.png",
                "caption");
        verify(client, never()).sendImage(
                eq("user"),
                eq(image.bytes()),
                eq("qwen-image-artifact.png"),
                eq("caption"));
        verify(client).sendTextWithTyping("user", "已生成", 1_000L);
    }

    @Test
    void routesPlantumlImageThroughSendFileEvenWhenGlobalFlagDisabled() throws Exception {
        // The plantuml- filename prefix is the canonical marker set by
        // DiagramCapability. It must always go through sendFile() to bypass
        // the iLink image CDN's 500s on small structured PNGs, regardless
        // of the global deliver-images-as-files flag.
        IlinkReplySender sender = new IlinkReplySender(new IlinkProperties());
        ILinkClient client = mock(ILinkClient.class);
        GeneratedMedia image = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "plantuml-er.png",
                0L,
                "ER 图 caption");

        sender.send(
                context(client),
                "fallback",
                new AgentReply("已生成", List.of(image)));

        verify(client).sendFile(
                "user",
                image.bytes(),
                "plantuml-er.png",
                "ER 图 caption");
        verify(client, never()).sendImage(
                eq("user"),
                eq(image.bytes()),
                eq("plantuml-er.png"),
                eq("ER 图 caption"));
        verify(client).sendTextWithTyping("user", "已生成", 1_000L);
    }

    @Test
    void leavesNonPlantumlImageOnSendImageWhenGlobalFlagDisabled() throws Exception {
        // A qwen-image output has a filename that does not start with
        // "plantuml-", so it must keep using sendImage() so the user sees
        // an inline thumbnail instead of a file attachment.
        IlinkReplySender sender = new IlinkReplySender(new IlinkProperties());
        ILinkClient client = mock(ILinkClient.class);
        GeneratedMedia image = new GeneratedMedia(
                GeneratedMediaKind.IMAGE,
                new byte[] {1, 2, 3},
                "qwen-image-artifact.png",
                0L,
                "caption");

        sender.send(
                context(client),
                "fallback",
                new AgentReply("已生成", List.of(image)));

        verify(client).sendImage(
                "user",
                image.bytes(),
                "qwen-image-artifact.png",
                "caption");
        verify(client, never()).sendFile(
                eq("user"),
                eq(image.bytes()),
                eq("qwen-image-artifact.png"),
                eq("caption"));
        verify(client).sendTextWithTyping("user", "已生成", 1_000L);
    }

    private static IlinkReplyContext context(ILinkClient client) {
        org.mockito.Mockito.when(client.getLoginContext()).thenReturn(new LoginContext(
                "login-token",
                "owner",
                "bot",
                "https://example.test"));
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setFrom_user_id("user");
        message.setTo_user_id("bot");
        message.setContext_token("reply-token");
        return IlinkReplyContext.open(client, message, "user");
    }
}
