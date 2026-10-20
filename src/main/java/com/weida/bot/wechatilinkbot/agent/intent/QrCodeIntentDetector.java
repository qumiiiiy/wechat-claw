package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit QR-code generation requests so the agent routes them to
 * the {@code generate_qr_code} tool instead of treating them as plain chat
 * or as image generation.
 *
 * @author PengChengLi
 * @since 2026-07-27
 */
@Component
public class QrCodeIntentDetector {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Loose pattern that catches "生成二维码 / 给我做个二维码 / 来个QR码 / make a qrcode"
     * while leaving generic mentions (e.g. "二维码是什么") to other detectors.
     */
    private static final Pattern QR_GENERATION_PATTERN = Pattern.compile(
            "(?:生成|制作|创建|做|来|出|画|给(?:我|你)?|帮(?:我|你)?).{0,15}"
                    + "(?:二维码|qr码|qrcode|qr码图)");

    private static final List<String> QR_KEYWORD_LIST = List.of(
            "二维码", "qr码", "qrcode");

    private static final List<String> QR_NEGATION_MARKER_LIST = List.of(
            "不要二维码", "不用二维码", "别二维码",
            "不要qr码", "不用qr码", "别qr码",
            "不要qrcode", "不用qrcode", "别qrcode");

    private static final List<String> RETRY_MARKER_LIST = List.of(
            "再试一次", "再试试", "重试一次", "重新试",
            "再来一次", "再来一遍", "重新来一次", "换一个试试");

    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        if (containsAny(normalized, QR_NEGATION_MARKER_LIST)) {
            return false;
        }
        // Generalised negation: "不要 / 不用 / 别" followed by any QR keyword
        // is treated as a rejection regardless of intervening words.
        if (startsWithNegation(normalized)
                && QR_KEYWORD_LIST.stream().anyMatch(normalized::contains)) {
            return false;
        }
        if (QR_GENERATION_PATTERN.matcher(normalized).find()) {
            return true;
        }
        // Short imperative forms ("二维码：xxx", "qr码:xxx") that the longer
        // generation pattern misses because they lack an action verb.
        return QR_KEYWORD_LIST.stream().anyMatch(normalized::contains)
                && (normalized.contains("：") || normalized.contains(":"));
    }

    private static boolean startsWithNegation(String normalized) {
        return normalized.startsWith("不要")
                || normalized.startsWith("不用")
                || normalized.startsWith("别");
    }

    /** Recognises a short retry when the immediately preceding user turn requested a QR code. */
    public boolean hasContinuationIntent(
            List<ConversationMessage> history,
            String currentText) {
        String normalized = normalize(currentText);
        if (containsAny(normalized, QR_NEGATION_MARKER_LIST)
                || !containsAny(normalized, RETRY_MARKER_LIST)) {
            return false;
        }
        if (history == null) {
            return false;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message != null && "user".equals(message.role())) {
                return hasIntent(message.content());
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> markerList) {
        return markerList.stream().anyMatch(text::contains);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(
                WHITESPACE_PATTERN.matcher(text).replaceAll("").toLowerCase(Locale.ROOT),
                Normalizer.Form.NFKC);
    }
}