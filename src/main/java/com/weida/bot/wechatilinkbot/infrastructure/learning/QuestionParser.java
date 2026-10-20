package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.ParseStatus;
import com.weida.bot.wechatilinkbot.model.QuestionOption;
import com.weida.bot.wechatilinkbot.model.QuestionSnapshot;
import com.weida.bot.wechatilinkbot.model.QuestionType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习通题目 DOM 解析器。
 * 从互动题 HTML 片段解析出稳定的 {@link QuestionSnapshot}。
 * 使用混合选择器策略：真实学习通 class 名优先，通用 class 降级。
 */
public class QuestionParser {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionParser.class);

    private static final Pattern LABEL_PATTERN = Pattern.compile("^([A-Z])\\s*[.、．:：]?\\s*");
    private static final Pattern QID_ATTR_PATTERN = Pattern.compile("^(\\d+|[a-zA-Z0-9_-]{4,64})$");

    /**
     * 解析题目 HTML 为快照。
     *
     * @param html      题目页面 HTML 片段
     * @param courseId   课程 ID（可空）
     * @param chapterId  章节 ID（可空）
     * @param pageSource 页面来源 URL（可空，会自动规范化）
     * @return 解析后的题目快照
     */
    public QuestionSnapshot parse(String html, String courseId, String chapterId, String pageSource) {
        String safeCourseId = courseId != null ? courseId.strip() : "";
        String safeChapterId = chapterId != null ? chapterId.strip() : "";
        String safeSource = QuestionDomainRecognizer.normalizePageSource(pageSource);
        Instant now = Instant.now();

        if (html == null || html.isBlank()) {
            return emptyResult(safeCourseId, safeChapterId, safeSource, now, ParseStatus.EMPTY_HTML);
        }

        try {
            Document doc = Jsoup.parse(html);
            return parseDocument(doc, safeCourseId, safeChapterId, safeSource, now);
        } catch (Exception exception) {
            LOG.warn("question_parse_error courseId={} chapterId={} error={}",
                    safeCourseId, safeChapterId, exception.getMessage());
            return emptyResult(safeCourseId, safeChapterId, safeSource, now, ParseStatus.PARSE_ERROR);
        }
    }

    private QuestionSnapshot parseDocument(
            Document doc,
            String courseId,
            String chapterId,
            String pageSource,
            Instant now) {

        // 检测 iframe 未加载
        if (hasUnloadedIframe(doc)) {
            return emptyResult(courseId, chapterId, pageSource, now, ParseStatus.IFRAME_NOT_LOADED);
        }

        // 检测学校 SSO / 登录页面（不应解析为题目）
        if (isLoginPage(doc)) {
            return emptyResult(courseId, chapterId, pageSource, now, ParseStatus.UNKNOWN_TYPE);
        }

        // 提取题型
        QuestionType questionType = extractQuestionType(doc);

        // 提取题干
        String stem = extractStem(doc);
        if (stem.isEmpty()) {
            return buildResult(
                    null, courseId, chapterId, questionType, "", List.of(), false,
                    pageSource, now, ParseStatus.MISSING_STEM);
        }

        // 提取选项（填空题可能没有选项）
        List<QuestionOption> options = extractOptions(doc);

        // 题型特定校验
        if (questionType == QuestionType.SINGLE_CHOICE || questionType == QuestionType.MULTIPLE_CHOICE
                || questionType == QuestionType.TRUE_FALSE) {
            if (options.isEmpty()) {
                return buildResult(
                        null, courseId, chapterId, questionType, stem, List.of(),
                        questionType == QuestionType.MULTIPLE_CHOICE,
                        pageSource, now, ParseStatus.EMPTY_OPTIONS);
            }
        }

        // 检查重复选项
        if (hasDuplicateOptions(options)) {
            return buildResult(
                    null, courseId, chapterId, questionType, stem, options,
                    questionType == QuestionType.MULTIPLE_CHOICE,
                    pageSource, now, ParseStatus.DUPLICATE_OPTIONS);
        }

        // 提取 questionId
        String questionId = extractQuestionId(doc);
        ParseStatus idStatus = ParseStatus.OK;
        String fingerprint = QuestionSnapshot.computeFingerprint(stem, options);

        if (questionId == null || questionId.isBlank()) {
            questionId = fingerprint;
            idStatus = ParseStatus.NO_STABLE_ID;
        }

        boolean allowMultiple = questionType == QuestionType.MULTIPLE_CHOICE;

        return new QuestionSnapshot(
                questionId,
                fingerprint,
                courseId,
                chapterId,
                questionType,
                stem,
                options,
                allowMultiple,
                pageSource,
                now,
                idStatus);
    }

    private boolean hasUnloadedIframe(Document doc) {
        Elements iframes = doc.select("iframe");
        if (iframes.isEmpty()) {
            return false;
        }
        for (Element iframe : iframes) {
            String src = iframe.attr("src");
            String srcdoc = iframe.attr("srcdoc");
            // iframe 有 srcdoc 内容视为已加载
            if (!srcdoc.isBlank()) {
                return false;
            }
            // iframe src 为空或指向外部视为未加载
            if (src.isBlank() || src.startsWith("http://") || src.startsWith("https://")) {
                return true;
            }
        }
        return false;
    }

    private boolean isLoginPage(Document doc) {
        String text = doc.text().toLowerCase(Locale.ROOT);
        return text.contains("请登录") || text.contains("please login")
                || text.contains("扫码登录") || text.contains("二维码")
                || text.contains("school_id") || text.contains("学校选择")
                || text.contains("统一身份认证") || text.contains("学号/工号")
                || text.contains("sso");
    }

    private QuestionType extractQuestionType(Document doc) {
        // 优先从精确选择器
        String typeText = extractTextBySelectors(doc,
                ".question-type",
                "[data-type]",
                ".Zy_TItle .type",
                "[class*=questionType]");

        if (!typeText.isBlank()) {
            QuestionType type = QuestionType.fromRawText(typeText);
            if (type != QuestionType.UNKNOWN) {
                return type;
            }
        }

        // 从 input type 推断
        Elements radios = doc.select("input[type=radio]");
        Elements checkboxes = doc.select("input[type=checkbox]");
        if (!checkboxes.isEmpty()) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (!radios.isEmpty()) {
            return QuestionType.SINGLE_CHOICE;
        }

        // 从 class 名推断
        String bodyClasses = doc.body() != null ? doc.body().className() : "";
        String classType = QuestionType.fromRawText(bodyClasses).displayName();
        if (!"未知".equals(classType)) {
            return QuestionType.fromRawText(bodyClasses);
        }

        // 从题干文本关键词推断
        String fullText = doc.text();
        if (fullText.contains("填空") || fullText.contains("请填写")) {
            return QuestionType.FILL_BLANK;
        }
        if (fullText.contains("判断") || fullText.contains("对还是错")) {
            return QuestionType.TRUE_FALSE;
        }
        if (fullText.contains("多选")) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        if (fullText.contains("单选")) {
            return QuestionType.SINGLE_CHOICE;
        }

        // 从选项数量推断
        List<QuestionOption> opts = extractOptions(doc);
        if (opts.size() == 2) {
            String joined = opts.get(0).text() + opts.get(1).text();
            if (joined.contains("对") && joined.contains("错")
                    || joined.contains("正确") && joined.contains("错误")
                    || joined.contains("是") && joined.contains("否")) {
                return QuestionType.TRUE_FALSE;
            }
        }

        // 如果有 input[type=text] 或 textarea，可能是填空
        if (!doc.select("input[type=text], textarea").isEmpty() && opts.isEmpty()) {
            return QuestionType.FILL_BLANK;
        }

        return QuestionType.UNKNOWN;
    }

    private String extractStem(Document doc) {
        // 精确选择器 → 模糊选择器 → 降级
        String stem = extractTextBySelectors(doc,
                ".question-stem",
                ".Zy_TItle",
                "[class*=stem]",
                "[class*=Stem]",
                ".questionContent",
                ".mark_name",
                "p.title");

        if (stem.isBlank()) {
            // 降级：取第一个有意义的 <p>
            Elements paragraphs = doc.select("p");
            for (Element p : paragraphs) {
                String text = p.text().strip();
                if (text.length() > 5 && !text.startsWith("A") && !text.startsWith("B")) {
                    return text;
                }
            }
        }
        return stem.strip();
    }

    private List<QuestionOption> extractOptions(Document doc) {
        List<QuestionOption> options = new ArrayList<>();

        // 从 list items 提取
        Elements items = doc.select(
                ".option-list li, .Zy_ulBottom li, [class*=option] li, .question-option");

        if (items.isEmpty()) {
            items = doc.select("li");
        }

        // 如果有 radio/checkbox，从它们的父元素提取
        if (items.isEmpty()) {
            Elements inputs = doc.select("input[type=radio], input[type=checkbox]");
            for (Element input : inputs) {
                Element parent = input.parent();
                if (parent != null) {
                    items = parent.parent() != null ? parent.parent().children() : new Elements(parent);
                    break;
                }
            }
        }

        for (Element item : items) {
            String text = item.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            String label = extractLabel(text);
            String optionText = stripLabel(text, label);
            if (!optionText.isEmpty()) {
                options.add(new QuestionOption(label, optionText));
            }
        }

        // 填空题特殊处理：从 input/textarea 提取
        if (options.isEmpty()) {
            Elements fillInputs = doc.select("input[type=text], textarea");
            int index = 0;
            for (Element fill : fillInputs) {
                String placeholder = fill.attr("placeholder");
                if (placeholder.isBlank()) {
                    placeholder = "空" + (index + 1);
                }
                options.add(new QuestionOption(String.valueOf((char) ('A' + index)), placeholder));
                index++;
            }
        }

        return options;
    }

    private String extractLabel(String text) {
        Matcher matcher = LABEL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return String.valueOf((char) ('A'));
    }

    private String stripLabel(String text, String label) {
        String stripped = text.strip();
        if (stripped.startsWith(label + ".") || stripped.startsWith(label + "、")
                || stripped.startsWith(label + "．") || stripped.startsWith(label + ":")
                || stripped.startsWith(label + "：")) {
            stripped = stripped.substring(label.length());
        }
        // 去掉前导标点
        stripped = stripped.replaceAll("^[.、．:：\\s]+", "");
        return stripped.strip();
    }

    private String extractQuestionId(Document doc) {
        // 优先从 data-qid
        Element el = doc.selectFirst("[data-qid]");
        if (el != null) {
            String qid = el.attr("data-qid").strip();
            if (isValidId(qid)) {
                return qid;
            }
        }
        // 从 data-id
        el = doc.selectFirst("[data-id]");
        if (el != null) {
            String qid = el.attr("data-id").strip();
            if (isValidId(qid)) {
                return qid;
            }
        }
        // 从 id 属性（排除 generic ids like "question"）
        el = doc.selectFirst("[id]");
        if (el != null) {
            String id = el.attr("id").strip();
            if (isValidId(id) && !id.equalsIgnoreCase("question") && !id.equalsIgnoreCase("form")) {
                return id;
            }
        }
        return null;
    }

    private boolean isValidId(String id) {
        return id != null && !id.isBlank() && QID_ATTR_PATTERN.matcher(id).matches();
    }

    private boolean hasDuplicateOptions(List<QuestionOption> options) {
        if (options.size() <= 1) {
            return false;
        }
        Set<String> seen = new HashSet<>();
        for (QuestionOption opt : options) {
            String normalized = QuestionSnapshot.normalizeText(opt.text());
            if (!seen.add(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String extractTextBySelectors(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String text = "";
                // 对于 data 属性选择器，取属性值
                if (selector.startsWith("[data-") && selector.endsWith("]")) {
                    String attrName = selector.substring(6, selector.length() - 1);
                    text = el.attr(attrName);
                } else {
                    text = el.text();
                }
                if (text != null && !text.isBlank()) {
                    return text.strip();
                }
            }
        }
        return "";
    }

    private QuestionSnapshot emptyResult(
            String courseId, String chapterId, String pageSource, Instant now, ParseStatus status) {
        return new QuestionSnapshot(
                "", "", courseId, chapterId, QuestionType.UNKNOWN, "", List.of(), false,
                pageSource, now, status);
    }

    private QuestionSnapshot buildResult(
            String questionId,
            String courseId,
            String chapterId,
            QuestionType questionType,
            String stem,
            List<QuestionOption> options,
            boolean allowMultiple,
            String pageSource,
            Instant now,
            ParseStatus status) {
        return new QuestionSnapshot(
                questionId != null ? questionId : "",
                "",
                courseId,
                chapterId,
                questionType,
                stem,
                options,
                allowMultiple,
                pageSource,
                now,
                status);
    }
}
