package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习通任务列表解析器。
 * 解析作业列表页面 HTML，提取任务信息，支持规范化类型和截止时间。
 */
@Component
public class TaskListParser {

    private static final Logger log = LoggerFactory.getLogger(TaskListParser.class);
    private static final Pattern WORK_ID_PATTERN = Pattern.compile("workId=(\\d+)");
    private static final Pattern COURSE_ID_PATTERN = Pattern.compile("courseId=(\\d+)");
    private static final Pattern CLASS_ID_PATTERN = Pattern.compile("classId=(\\d+)");
    private static final Pattern RELATIVE_REMAINING_PATTERN = Pattern.compile(
            "(?:剩余|还剩|还有)\\s*(?:(\\d+)\\s*天)?\\s*(?:(\\d+)\\s*小时)?\\s*(?:(\\d+)\\s*分钟)?");

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ABSOLUTE_DATE_TIME =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm")
                    .optionalStart().appendPattern(":ss").optionalEnd()
                    .toFormatter();
    private static final DateTimeFormatter ABSOLUTE_DATE_TIME_SLASH =
            DateTimeFormatter.ofPattern("yyyy/M/d HH:mm");
    private static final DateTimeFormatter ABSOLUTE_DATE_TIME_CN =
            DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
    private static final DateTimeFormatter MONTH_DAY_TIME =
            DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final Clock clock;

    public TaskListParser() {
        this(Clock.system(SHANGHAI));
    }

    TaskListParser(Clock clock) {
        this.clock = clock;
    }

    /**
     * 解析任务列表 HTML，提取任务信息。
     *
     * @param html 任务列表页面 HTML
     * @param courseId 课程 ID（用于去重键）
     * @param source 来源标识
     * @return 任务列表
     */
    public List<CourseTask> parseTaskList(String html, String courseId, String source) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        String safeCourseId = courseId != null ? courseId.strip() : "";
        String safeSource = source != null ? source.strip() : "chaoxing";

        try {
            Document doc = Jsoup.parse(html);
            Elements taskItems = doc.select(".bottomList ul li");

            if (taskItems.isEmpty()) {
                // 尝试备用选择器
                taskItems = doc.select(".task-item");
            }

            if (taskItems.isEmpty()) {
                taskItems = doc.select("li:has(a[href*=workId])");
            }

            if (taskItems.isEmpty()) {
                String snippet = html.length() > 2000 ? html.substring(0, 2000) : html;
                log.warn("chaoxing_task_parse_no_items courseId={} selectors=\".bottomList ul li\", \".task-item\", \"li:has(a[href*=workId])\" htmlSnippet={}",
                        safeCourseId, snippet);
                return Collections.emptyList();
            }

            List<CourseTask> tasks = new ArrayList<>();
            for (Element item : taskItems) {
                CourseTask task = parseTaskItem(item, safeCourseId, safeSource);
                if (task != null) {
                    tasks.add(task);
                }
            }
            return Collections.unmodifiableList(tasks);
        } catch (Exception e) {
            log.warn("chaoxing_task_parse_failed courseId={}", safeCourseId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 兼容旧调用的重载方法。
     */
    public List<CourseTask> parseTaskList(String html) {
        return parseTaskList(html, "", "chaoxing");
    }

    private CourseTask parseTaskItem(Element item, String courseId, String source) {
        StringBuilder parseNote = new StringBuilder();

        // 获取任务 ID
        String taskId = extractTaskId(item, parseNote);
        if (taskId == null || taskId.isBlank()) {
            return null;
        }

        // 获取任务名称
        String taskName = extractTaskName(item, parseNote);
        if (taskName == null || taskName.isBlank()) {
            return null;
        }

        // 获取并规范化任务类型
        String rawTaskType = extractRawTaskType(item);
        TaskType taskType = TaskType.fromRawText(rawTaskType);
        if (taskType == TaskType.UNKNOWN && rawTaskType != null && !rawTaskType.isBlank()) {
            parseNote.append("未识别的任务类型:").append(rawTaskType).append("; ");
        }

        // 获取并规范化完成状态；文本缺失时再按图标判断
        String rawStatus = extractRawStatus(item);
        CompletionStatus completionStatus = rawStatus != null
                ? CompletionStatus.fromRawText(rawStatus)
                : CompletionStatus.UNKNOWN;
        if (completionStatus == CompletionStatus.UNKNOWN) {
            completionStatus = completionStatusFromIcon(item);
        }
        if (completionStatus == CompletionStatus.UNKNOWN
                && rawStatus != null && !rawStatus.isBlank()) {
            parseNote.append("未识别的状态:").append(rawStatus).append("; ");
        }

        // 解析截止时间
        Instant deadline = extractDeadline(item, parseNote);

        return new CourseTask(
                taskId,
                taskName,
                taskType,
                completionStatus,
                deadline,
                courseId,
                source,
                parseNote.toString().strip()
        );
    }

    private String extractTaskId(Element item, StringBuilder parseNote) {
        // 尝试从 data 属性提取 workId
        String taskUrl = item.attr("data");
        if (taskUrl != null && !taskUrl.isBlank()) {
            String workId = extractParam(taskUrl, WORK_ID_PATTERN);
            if (workId != null && !workId.isBlank()) {
                return workId;
            }
        }

        // 尝试从 data-taskid 属性提取
        String dataTaskId = item.attr("data-taskid");
        if (dataTaskId != null && !dataTaskId.isBlank()) {
            return dataTaskId.strip();
        }

        // 尝试从链接提取
        Element link = item.selectFirst("a[href]");
        if (link != null) {
            String href = link.attr("href");
            if (href != null && !href.isBlank()) {
                String workId = extractParam(href, WORK_ID_PATTERN);
                if (workId != null && !workId.isBlank()) {
                    return workId;
                }
            }
        }

        parseNote.append("缺少任务ID; ");
        return null;
    }

    private String extractTaskName(Element item, StringBuilder parseNote) {
        // 尝试多种选择器
        Element nameElement = item.selectFirst("p.overHidden2");
        if (nameElement == null) {
            nameElement = item.selectFirst(".task-title");
        }
        if (nameElement == null) {
            nameElement = item.selectFirst(".overHidden");
        }
        if (nameElement == null) {
            nameElement = item.selectFirst("p[class*=title]");
        }

        if (nameElement == null) {
            parseNote.append("缺少任务标题; ");
            return null;
        }

        String name = nameElement.text();
        if (name == null || name.isBlank()) {
            parseNote.append("任务标题为空; ");
            return null;
        }
        return name.strip();
    }

    private String extractRawTaskType(Element item) {
        // 尝试从图标类名提取
        Element iconElement = item.selectFirst(".tag.work-icon");
        if (iconElement != null) {
            String className = iconElement.className();
            if (className.contains("icon-zy-g") || className.contains("icon-zy")) {
                return "作业";
            } else if (className.contains("icon-sp")) {
                return "视频";
            } else if (className.contains("icon-tl")) {
                return "讨论";
            } else if (className.contains("icon-hp-gy") || className.contains("icon-hp")) {
                return "作业";
            } else if (className.contains("icon-exam-g") || className.contains("icon-exam")) {
                return "考试";
            } else if (className.contains("icon-qt")) {
                return "其他";
            }
        }

        // 尝试从文本提取
        Element typeElement = item.selectFirst(".task-type");
        if (typeElement != null) {
            return typeElement.text();
        }

        // 尝试从类名提取
        String itemClass = item.className();
        if (itemClass.contains("homework") || itemClass.contains("work")) {
            return "作业";
        }
        if (itemClass.contains("exam") || itemClass.contains("test")) {
            return "考试";
        }
        if (itemClass.contains("chapter") || itemClass.contains("video")) {
            return "章节";
        }

        return null;
    }

    private String extractRawStatus(Element item) {
        Element statusElement = item.selectFirst("p.status");
        if (statusElement == null) {
            statusElement = item.selectFirst(".task-status");
        }
        if (statusElement == null) {
            statusElement = item.selectFirst("[class*=status]");
        }
        if (statusElement == null) {
            statusElement = item.selectFirst(".answerStatus, [class*=answer]");
        }
        if (statusElement == null) {
            statusElement = item.selectFirst(".stuStatus");
        }
        if (statusElement == null) {
            statusElement = item.selectFirst(".statusbg");
        }
        if (statusElement != null && !statusElement.text().isBlank()) {
            return statusElement.text();
        }
        String itemText = item.text();
        if (itemText.contains("未完成") || itemText.contains("未交") || itemText.contains("未提交")) {
            return "未完成";
        }
        if (itemText.contains("已完成") || itemText.contains("已交") || itemText.contains("已提交")) {
            return "已完成";
        }
        return null;
    }

    private CompletionStatus completionStatusFromIcon(Element item) {
        Element iconElement = item.selectFirst(".tag.work-icon, .work-icon");
        if (iconElement == null) {
            return CompletionStatus.UNKNOWN;
        }
        String className = iconElement.className();
        if (className.contains("icon-zy-g")
                || className.contains("icon-hp-gy")
                || className.contains("icon-exam-g")) {
            return CompletionStatus.COMPLETED;
        }
        if (className.contains("icon-zy")
                || className.contains("icon-hp")
                || className.contains("icon-exam")) {
            return CompletionStatus.PENDING;
        }
        return CompletionStatus.UNKNOWN;
    }

    private Instant extractDeadline(Element item, StringBuilder parseNote) {
        // 尝试多种选择器
        Element deadlineElement = item.selectFirst(".task-deadline");
        if (deadlineElement == null) {
            deadlineElement = item.selectFirst("[class*=deadline]");
        }
        if (deadlineElement == null) {
            deadlineElement = item.selectFirst("[class*=time]");
        }

        if (deadlineElement == null) {
            return null;
        }

        String deadlineText = deadlineElement.text();
        if (deadlineText == null || deadlineText.isBlank()) {
            return null;
        }

        return parseDeadlineText(deadlineText.strip(), parseNote);
    }

    private Instant parseDeadlineText(String text, StringBuilder parseNote) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isBlank() || isNoDeadline(normalized)) {
            return null;
        }
        try {
            return LocalDateTime.parse(normalized, ABSOLUTE_DATE_TIME)
                    .atZone(SHANGHAI)
                    .toInstant();
        } catch (DateTimeParseException e) {
            // try other formats below
        }
        try {
            return LocalDateTime.parse(normalized, ABSOLUTE_DATE_TIME_SLASH)
                    .atZone(SHANGHAI)
                    .toInstant();
        } catch (DateTimeParseException e) {
            // try next format
        }
        try {
            return LocalDateTime.parse(normalized, ABSOLUTE_DATE_TIME_CN)
                    .atZone(SHANGHAI)
                    .toInstant();
        } catch (DateTimeParseException e) {
            // try next format
        }
        try {
            TemporalAccessor parsed = MONTH_DAY_TIME.parse(normalized);
            int month = parsed.get(ChronoField.MONTH_OF_YEAR);
            int day = parsed.get(ChronoField.DAY_OF_MONTH);
            int hour = parsed.get(ChronoField.HOUR_OF_DAY);
            int minute = parsed.get(ChronoField.MINUTE_OF_HOUR);
            return LocalDate.now(clock)
                    .withMonth(month)
                    .withDayOfMonth(day)
                    .atTime(hour, minute)
                    .atZone(SHANGHAI)
                    .toInstant();
        } catch (DateTimeParseException e) {
            // try relative
        }
        Instant relative = parseRelativeDeadline(normalized);
        if (relative != null) {
            return relative;
        }
        parseNote.append("无效的截止时间格式:").append(text).append("; ");
        return null;
    }

    private static boolean isNoDeadline(String text) {
        return text.contains("无限制")
                || text.contains("不限")
                || text.contains("手动结束")
                || text.contains("长期有效")
                || text.contains("未设置");
    }

    private Instant parseRelativeDeadline(String text) {
        Matcher matcher = RELATIVE_REMAINING_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        long days = durationGroup(matcher.group(1));
        long hours = durationGroup(matcher.group(2));
        long minutes = durationGroup(matcher.group(3));
        if (days == 0 && hours == 0 && minutes == 0) {
            return null;
        }
        return Instant.now(clock)
                .plus(Duration.ofDays(days).plusHours(hours).plusMinutes(minutes));
    }

    private static long durationGroup(String value) {
        return value == null ? 0 : Long.parseLong(value);
    }

    private String extractParam(String url, Pattern pattern) {
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从任务列表页面提取进度信息。
     *
     * @param html 任务列表页面 HTML
     * @return 进度信息，格式如 "5/5"
     */
    public String extractProgress(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        try {
            Document doc = Jsoup.parse(html);
            Elements spans = doc.select(".work-progress-box span");
            for (Element span : spans) {
                String text = span.text();
                if (text != null && text.contains("/")) {
                    return text;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
