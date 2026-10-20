package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit image-generation intent without confusing image understanding requests.
 *
 * @author ryx
 * @since 2026-07-21
 */
@Component
public class ImageIntentDetector {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern IMAGE_GENERATION_PATTERN = Pattern.compile(
            "(?:生成|绘制|制作|创建|设计|做|画|来|出).{0,40}"
                    + "(?:图片|图像|图画|图|海报|壁纸|头像|插画)");
    private static final Pattern SCENE_GENERATION_PATTERN = Pattern.compile(
            "(?:生成|绘制|制作|创建|设计|做|画|来|出).{0,20}"
                    + "(?:一张|一幅|一副).{1,40}"
                    + "(?:风景|风光|景色|景象|场景|画面|图景|湖|山|海|天空|城市|"
                    + "人物|动物|建筑|街景)");
    private static final Pattern VISUAL_DESIRE_PATTERN = Pattern.compile(
            "(?:我?想看|想看看|想要看|给我看(?:看)?|让我看(?:看)?|展示(?:一下)?|呈现(?:一下)?)"
                    + ".{1,60}(?:画面|场景|景象|图景|样子|效果图|视觉效果)");
    private static final List<String> IMAGE_GENERATION_MARKER_LIST = List.of(
            "生成图片", "生成一张图片", "生成一幅图", "生成一张图", "再生成图片",
            "画图", "画一张", "画一幅", "帮我画", "给我画", "绘制图片", "绘制一张",
            "制作图片", "制作一张图", "创建图片", "创建一张图", "做张图", "做一张图",
            "来张图", "来一张图", "出张图", "出图", "生成海报", "设计海报",
            "生成壁纸", "生成头像", "生成插画");
    private static final List<String> IMAGE_NEGATION_MARKER_LIST = List.of(
            "不要生成图片", "不用生成图片", "别生成图片", "不要画图", "不用画图",
            "别画图", "不要画", "不用画", "别画");
    private static final List<String> IMAGE_UNDERSTANDING_MARKER_LIST = List.of(
            "这张图", "这幅图", "这张图片", "这幅图片", "我发的图", "刚发的图",
            "刚才的图", "上面的图", "图片里", "照片里", "图中", "视频里");
    private static final List<String> RETRY_MARKER_LIST = List.of(
            "再试一次", "再试试", "重试一次", "重新试", "再来一次", "再来一遍",
            "重新来一次", "换一个试试");

    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        if (containsAny(normalized, IMAGE_NEGATION_MARKER_LIST)) {
            return false;
        }
        boolean explicitGeneration = IMAGE_GENERATION_MARKER_LIST.stream()
                .anyMatch(normalized::contains)
                || IMAGE_GENERATION_PATTERN.matcher(normalized).find()
                || SCENE_GENERATION_PATTERN.matcher(normalized).find();
        if (explicitGeneration) {
            return true;
        }
        return !containsAny(normalized, IMAGE_UNDERSTANDING_MARKER_LIST)
                && VISUAL_DESIRE_PATTERN.matcher(normalized).find();
    }

    /** Recognizes a short retry when the immediately preceding user turn requested an image. */
    public boolean hasContinuationIntent(
            List<ConversationMessage> history,
            String currentText) {
        String normalized = normalize(currentText);
        if (containsAny(normalized, IMAGE_NEGATION_MARKER_LIST)
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
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE_PATTERN
                .matcher(text.toLowerCase(Locale.ROOT))
                .replaceAll("");
    }
}
