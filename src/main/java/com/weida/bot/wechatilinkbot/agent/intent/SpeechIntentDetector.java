package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit natural-language speech-output intent with negation precedence.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class SpeechIntentDetector {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern SPEECH_GENERATION_PATTERN = Pattern.compile(
            "(?:生成|合成|制作|创建|转换|转成|发|来|回复|回答|换).{0,40}"
                    + "(?:语音|声音|音频|女声|男声|配音)");
    private static final List<String> SPEECH_INTENT_MARKER_LIST = List.of(
            "用语音回复", "语音回复", "用语音回答", "语音回答", "用语音说", "用语音告诉",
            "给我发语音", "发段语音", "发个语音", "来段语音", "来一段语音", "来个语音",
            "生成语音", "生成一段语音", "再生成语音", "重新生成语音", "重做语音",
            "生成语音给我", "合成一段语音", "转换成语音", "转成语音", "再说一遍",
            "再念一遍", "再读一遍", "再来一段声音", "换个声音", "换一种声音",
            "用孩子的声音说", "用儿童声音说", "用童声说", "用小朋友的声音说",
            "用声音回复", "用声音回答", "用声音说", "读给我听", "念给我听", "说给我听",
            "讲给我听", "唱给我听", "想听你", "让我听听", "帮我朗读", "请朗读",
            "朗读一下", "朗读这", "帮我配音", "请配音", "帮我唱", "请唱", "唱一首",
            "唱首", "唱生日歌", "voicereply", "readaloud", "texttospeech", "tts", "singforme");
    private static final List<String> SPEECH_NEGATION_MARKER_LIST = List.of(
            "不要用语音", "不用语音", "别用语音", "不要发语音", "别发语音", "不想听语音",
            "不要朗读", "不用朗读", "别朗读", "不要配音", "不用配音", "别配音",
            "不要唱", "不用唱", "别唱", "不想听你", "novoice", "donotreadaloud");
    private static final List<String> RETRY_MARKER_LIST = List.of(
            "再试一次", "再试试", "重试一次", "重新试", "再来一次", "再来一遍",
            "重新来一次", "换一个试试");

    /**
     * Detects speech intent in one natural-language input.
     *
     * @param text user text or transcript
     * @return {@code true} only for an explicit non-negated request
     */
    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        if (isNegated(normalized)) {
            return false;
        }
        return SPEECH_INTENT_MARKER_LIST.stream().anyMatch(normalized::contains)
                || SPEECH_GENERATION_PATTERN.matcher(normalized).find();
    }

    /**
     * Recognizes a short retry as speech when the immediately preceding user turn requested speech.
     *
     * @param history recent conversation history
     * @param currentText current normalized prompt
     * @return {@code true} for a speech-generation continuation
     */
    public boolean hasContinuationIntent(
            List<ConversationMessage> history,
            String currentText) {
        String normalized = normalize(currentText);
        if (isNegated(normalized) || !containsAny(normalized, RETRY_MARKER_LIST)) {
            return false;
        }
        return latestUserMessage(history).map(this::hasIntent).orElse(false);
    }

    private static Optional<String> latestUserMessage(
            List<ConversationMessage> history) {
        if (history == null) {
            return Optional.empty();
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationMessage message = history.get(index);
            if (message != null && "user".equals(message.role())) {
                return Optional.ofNullable(message.content());
            }
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, List<String> markerList) {
        return markerList.stream().anyMatch(text::contains);
    }

    private static boolean isNegated(String text) {
        return containsAny(text, SPEECH_NEGATION_MARKER_LIST);
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE_PATTERN
                .matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll("");
    }
}
