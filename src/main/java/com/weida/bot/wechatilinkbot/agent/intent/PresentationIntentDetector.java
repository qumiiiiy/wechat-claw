package com.weida.bot.wechatilinkbot.agent.intent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detects explicit whole-deck presentation requests. */
@Component
public class PresentationIntentDetector {

    private static final Pattern PRESENTATION_PATTERN = Pattern.compile(
            "(?:pptx?|幻灯片|演示文稿|演示稿|汇报材料|做个演示|生成.{0,8}课件)",
            Pattern.CASE_INSENSITIVE);

    public boolean hasIntent(String text) {
        return PRESENTATION_PATTERN.matcher(normalize(text)).find();
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                        .toLowerCase(Locale.ROOT);
    }
}
