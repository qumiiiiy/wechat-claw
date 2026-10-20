package com.weida.bot.wechatilinkbot.agent.intent;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit references to a recently generated image.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Component
public class ImageReferenceDetector {

    private static final Pattern IMAGE_REFERENCE_PATTERN = Pattern.compile(
            "(?s).*(?:刚才|之前|上一张|那张|刚生成|刚画)"
                    + ".*(?:图|图片|画面|颜色|样子|它).*"
                    + "|.*(?:图|图片|画面).*(?:刚才|之前|上一张|那张|刚生成|刚画).*"
    );

    /** Returns whether the prompt clearly refers to a previous generated image. */
    public boolean hasIntent(String prompt) {
        return prompt != null
                && !prompt.isBlank()
                && IMAGE_REFERENCE_PATTERN.matcher(prompt).matches();
    }
}
