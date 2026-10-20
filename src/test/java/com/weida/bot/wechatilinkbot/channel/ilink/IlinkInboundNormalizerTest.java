package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.FileItem;
import com.github.wechat.ilink.sdk.core.model.ImageItem;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.VideoItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.weida.bot.wechatilinkbot.capability.DocumentCapability;
import com.weida.bot.wechatilinkbot.model.MediaKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IlinkInboundNormalizerTest {

    private ILinkClient client;
    private DocumentCapability documentCapability;
    private IlinkInboundNormalizer normalizer;

    @BeforeEach
    void setUp() {
        client = mock(ILinkClient.class);
        when(client.getLoginContext()).thenReturn(new LoginContext(
                "token",
                "owner",
                "bot",
                "https://example.test"));
        documentCapability = mock(DocumentCapability.class);
        normalizer = new IlinkInboundNormalizer(
                new IlinkProperties(),
                mock(IlinkMediaStorage.class),
                documentCapability);
    }

    @Test
    void normalizesTextWithoutExposingSdkTypes() {
        WeixinMessage message = message(List.of(MessageItem.text("测试消息")));

        IlinkInboundMessage inbound = normalizer.normalize(client, message, false, 1024);

        assertThat(inbound.prompt()).contains("用户文本", "测试消息");
        assertThat(inbound.currentUserText()).isEqualTo("测试消息");
        assertThat(inbound.fallback()).contains("已收到您的消息");
        assertThat(inbound.mediaParts()).isEmpty();
    }

    @Test
    void keepsImageAndVideoAttachmentsInMessageOrder() throws Exception {
        MessageItem image = new MessageItem();
        image.setType(2);
        image.setImage_item(new ImageItem());
        MessageItem video = new MessageItem();
        video.setType(5);
        video.setVideo_item(new VideoItem());
        when(client.downloadMediaFromMessageItem(image))
                .thenReturn(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
        when(client.downloadMediaFromMessageItem(video))
                .thenReturn(new byte[] {0x00, 0x00, 0x00, 0x18});

        IlinkInboundMessage inbound = normalizer.normalize(
                client,
                message(List.of(image, video)),
                true,
                1024);

        assertThat(inbound.mediaParts())
                .extracting(media -> media.kind())
                .containsExactly(MediaKind.IMAGE, MediaKind.VIDEO);
    }

    @Test
    void includesExtractedDocumentTextInThePrompt() throws Exception {
        FileItem file = new FileItem();
        file.setFile_name("guide.pdf");
        MessageItem item = new MessageItem();
        item.setType(6);
        item.setFile_item(file);
        byte[] bytes = new byte[] {1, 2, 3};
        when(client.downloadMediaFromMessageItem(item)).thenReturn(bytes);
        when(documentCapability.extract(bytes, "guide.pdf"))
                .thenReturn("第一章：模块化架构");

        IlinkInboundMessage inbound = normalizer.normalize(
                client,
                message(List.of(item)),
                true,
                1024);

        assertThat(inbound.prompt()).contains("guide.pdf", "第一章：模块化架构");
    }

    private static WeixinMessage message(List<MessageItem> itemList) {
        WeixinMessage message = new WeixinMessage();
        message.setMessage_id(123L);
        message.setMessage_type(1);
        message.setFrom_user_id("sender");
        message.setCreate_time_ms(System.currentTimeMillis());
        message.setItem_list(itemList);
        return message;
    }
}
