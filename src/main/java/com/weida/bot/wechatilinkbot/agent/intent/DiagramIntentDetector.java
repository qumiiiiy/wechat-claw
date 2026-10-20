package com.weida.bot.wechatilinkbot.agent.intent;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detects explicit structural-diagram (PlantUML) requests so the agent routes
 * them to the {@code generate_diagram} tool instead of the generic image
 * generator.
 *
 * <p>The detector recognises Chinese diagram-type keywords (流程图, 时序图,
 * 类图, 状态机, ER 图, 思维导图, 甘特图, UML 图…), the literal PlantUML
 * start markers ({@code @startuml}, {@code @startmindmap}, …), and
 * explicitly negative phrasings such as "不要时序图".</p>
 *
 * @author ryx
 * @since 2026-07-27
 */
@Component
public class DiagramIntentDetector {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final List<String> DIAGRAM_KEYWORDS = List.of(
            "时序图",
            "序列图",
            "流程图",
            "程序图",
            "类图",
            "状态机",
            "状态图",
            "活动图",
            "用例图",
            "组件图",
            "部署图",
            "对象图",
            "er图",
            "思维导图",
            "脑图",
            "甘特图",
            "架构图");

    /**
     * Lowercase "uml图" so the marker matches "来一个uml图" without being
     * confused by stray English fragments such as the bare word "SEQUENCE".
     */
    private static final String UML_FIGURE_MARKER = "uml图";

    private static final List<String> PLANTUML_MARKERS = List.of(
            "@startuml",
            "@startmindmap",
            "@startwbs",
            "@startgantt",
            "@startsalt",
            "@startjson",
            "@endmindmap");

    private static final List<String> NEGATION_MARKER_LIST = List.of(
            "不要时序图", "不用时序图", "别时序图",
            "不要序列图", "不用序列图", "别序列图",
            "不要流程图", "不用流程图", "别流程图",
            "不要类图",   "不用类图",   "别类图",
            "不要状态机", "不用状态机", "别状态机",
            "不要状态图", "不用状态图", "别状态图",
            "不要活动图", "不用活动图", "别活动图",
            "不要用例图", "不用用例图", "别用例图",
            "不要组件图", "不用组件图", "别组件图",
            "不要部署图", "不用部署图", "别部署图",
            "不要对象图", "不用对象图", "别对象图",
            "不要er图",   "不用er图",   "别er图",
            "不要思维导图", "不用思维导图", "别思维导图",
            "不要脑图",   "不用脑图",   "别脑图",
            "不要甘特图", "不用甘特图", "别甘特图",
            "不要架构图", "不用架构图", "别架构图",
            "不要uml图",  "不用uml图",  "别uml图",
            "不要plantuml", "不用plantuml", "别plantuml");

    private static final List<String> RETRY_MARKER_LIST = List.of(
            "再试一次", "再试试", "重试一次", "重新试",
            "再来一次", "再来一遍", "重新来一次", "换一个试试");

    public boolean hasIntent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        if (containsAny(normalized, NEGATION_MARKER_LIST)) {
            return false;
        }
        if (DIAGRAM_KEYWORDS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        if (normalized.contains(UML_FIGURE_MARKER)) {
            return true;
        }
        if (PLANTUML_MARKERS.stream().anyMatch(normalized::contains)) {
            return true;
        }
        return normalized.contains("@enduml");
    }

    /** Recognises a short retry when the immediately preceding user turn was a diagram. */
    public boolean hasContinuationIntent(
            List<ConversationMessage> history,
            String currentText) {
        String normalized = normalize(currentText);
        if (containsAny(normalized, NEGATION_MARKER_LIST)
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