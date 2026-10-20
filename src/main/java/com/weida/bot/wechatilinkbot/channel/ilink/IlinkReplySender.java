package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Delivers agent output only through a live trusted inbound reply context. */
@Component
class IlinkReplySender {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkReplySender.class);
    private static final long TEXT_TYPING_DURATION_MILLIS = 1_000L;
    private static final String SPEECH_FOLLOW_UP =
            "这段声音听起来怎么样？也可以说说你更喜欢怎样的语速、音色或情绪。";
    private static final String PLANTUML_FILE_PREFIX = "plantuml-";

    private final IlinkProperties properties;

    IlinkReplySender(IlinkProperties properties) {
        this.properties = properties;
    }

    void send(
            IlinkReplyContext context,
            String fallback,
            AgentReply agentReply) {
        MediaDelivery delivery = sendMedia(context, agentReply);
        String text = agentReply.text().isBlank() ? fallback : agentReply.text();
        if (!agentReply.generatedMedia().isEmpty() && delivery.sentCount == 0) {
            text = "媒体内容已经生成，但发送到微信失败了，请稍后再试。";
        } else if (delivery.speechRequested && !delivery.speechSent) {
            text = "这次语音暂时没能送达，请稍后再试。";
        } else if (delivery.speechSent) {
            text = SPEECH_FOLLOW_UP;
        }
        sendText(context, fallback, text);
    }

    void sendText(
            IlinkReplyContext context,
            String fallback,
            String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        int characterCount = text.codePointCount(0, text.length());
        if (characterCount > properties.getAutoReplyFileThresholdCharacters()) {
            sendTextFile(context, fallback, text, characterCount);
            return;
        }
        try {
            context.client().sendTextWithTyping(
                    context.targetUserId(), text, TEXT_TYPING_DURATION_MILLIS);
            LOG.info(
                    "wechat_reply_sent target={} sourceMessageRef={} agent={} textLength={}",
                    mask(context.targetUserId()),
                    IlinkLogReference.of(context.sourceMessageId()),
                    !text.equals(fallback),
                    text.length());
        } catch (IOException | RuntimeException exception) {
            LOG.error(
                    "wechat_reply_failed target={} sourceMessageRef={}",
                    safeMaskedTarget(context),
                    safeSourceMessageId(context),
                    exception);
        }
    }

    private MediaDelivery sendMedia(
            IlinkReplyContext context,
            AgentReply agentReply) {
        int sentCount = 0;
        boolean speechRequested = false;
        boolean speechSent = false;
        for (GeneratedMedia media : agentReply.generatedMedia()) {
            speechRequested = speechRequested
                    || media.kind() == GeneratedMediaKind.SPEECH_VIDEO;
            try {
                speechSent = sendMedia(context, media) || speechSent;
                sentCount++;
                LOG.info(
                        "wechat_generated_media_sent target={} sourceMessageRef={} mediaType={} bytes={}",
                        mask(context.targetUserId()),
                        IlinkLogReference.of(context.sourceMessageId()),
                        media.kind(),
                        media.bytes().length);
            } catch (IOException | RuntimeException exception) {
                LOG.error(
                        "wechat_generated_media_send_failed target={} sourceMessageRef={} mediaType={}",
                        safeMaskedTarget(context),
                        safeSourceMessageId(context),
                        media.kind(),
                        exception);
            }
        }
        return new MediaDelivery(sentCount, speechRequested, speechSent);
    }

    private boolean sendMedia(
            IlinkReplyContext context,
            GeneratedMedia media) throws IOException {
        ILinkClient client = context.client();
        String target = context.targetUserId();
        switch (media.kind()) {
            case IMAGE -> {
                boolean plantumlOutput = media.fileName() != null
                        && media.fileName().startsWith(PLANTUML_FILE_PREFIX);
                boolean useFileChannel = plantumlOutput
                        || properties.isDeliverImagesAsFiles();
                if (useFileChannel) {
                    LOG.info(
                            "wechat_image_delivered_as_file target={} bytes={} reason={}",
                            mask(target),
                            media.bytes().length,
                            plantumlOutput ? "plantuml_filename" : "global_flag");
                    client.sendFile(target, media.bytes(), media.fileName(), media.caption());
                } else {
                    client.sendImage(target, media.bytes(), media.fileName(), media.caption());
                }
                return false;
            }
            case FILE -> {
                client.sendFile(target, media.bytes(), media.fileName(), media.caption());
                return false;
            }
            case SPEECH_VIDEO -> {
                int durationMillis = Math.toIntExact(Math.min(
                        media.durationMillis(), (long) Integer.MAX_VALUE));
                client.sendVideo(
                        target,
                        media.bytes(),
                        media.fileName(),
                        durationMillis,
                        media.caption());
                return true;
            }
            default -> throw new IllegalArgumentException(
                    "不支持的生成媒体类型：" + media.kind());
        }
    }

    private static void sendTextFile(
            IlinkReplyContext context,
            String fallback,
            String text,
            int characterCount) {
        String fileName = "ai-reply-" + context.sourceMessageId() + ".txt";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try {
            context.client().sendFile(context.targetUserId(), bytes, fileName, "");
            LOG.info(
                    "wechat_reply_file_sent target={} sourceMessageRef={} agent={} characters={}",
                    mask(context.targetUserId()),
                    IlinkLogReference.of(context.sourceMessageId()),
                    !text.equals(fallback),
                    characterCount);
        } catch (IOException | RuntimeException exception) {
            LOG.error(
                    "wechat_reply_file_failed target={} sourceMessageRef={} characters={}",
                    safeMaskedTarget(context),
                    safeSourceMessageId(context),
                    characterCount,
                    exception);
            sendFailureNotice(context);
        }
    }

    private static void sendFailureNotice(IlinkReplyContext context) {
        try {
            context.client().sendTextWithTyping(
                    context.targetUserId(),
                    "回复内容较长，但文件发送失败了，请稍后再试。",
                    TEXT_TYPING_DURATION_MILLIS);
        } catch (IOException | RuntimeException exception) {
            LOG.error(
                    "wechat_reply_file_notice_failed target={} sourceMessageRef={}",
                    safeMaskedTarget(context),
                    safeSourceMessageId(context),
                    exception);
        }
    }

    private static String safeMaskedTarget(IlinkReplyContext context) {
        try {
            return mask(context.targetUserId());
        } catch (RuntimeException exception) {
            return "****";
        }
    }

    private static String safeSourceMessageId(IlinkReplyContext context) {
        try {
            return IlinkLogReference.of(context.sourceMessageId());
        } catch (RuntimeException exception) {
            return "none";
        }
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    private record MediaDelivery(
            int sentCount,
            boolean speechRequested,
            boolean speechSent) {
    }
}
