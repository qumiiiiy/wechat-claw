package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingClient;
import com.weida.bot.wechatilinkbot.infrastructure.learning.CourseJsonParser.CourseDetail;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginChallenge;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginException;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginService;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingLoginStatus;
import com.weida.bot.wechatilinkbot.infrastructure.learning.ChaoxingProperties;
import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 学习通课程采集能力。
 * 封装课程查询和任务查询的业务逻辑。
 */
@Component
public class LearningCapability {

    private static final Logger log = LoggerFactory.getLogger(LearningCapability.class);
    private static final int MAX_PENDING_TASKS = 100;
    private static final int MAX_VISIBLE_TASKS = 100;
    private static final Pattern DATE_PREFIX_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TASK_DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(SHANGHAI_ZONE);

    private final ChaoxingClient chaoxingClient;
    private final ChaoxingProperties properties;
    private final ChaoxingLoginService loginService;
    private final OcsCourseReferenceResolver courseReferenceResolver;

    @Autowired
    public LearningCapability(ChaoxingClient chaoxingClient, ChaoxingProperties properties,
                              ChaoxingLoginService loginService,
                              OcsCourseReferenceResolver courseReferenceResolver) {
        this.chaoxingClient = chaoxingClient;
        this.properties = properties;
        this.loginService = loginService;
        this.courseReferenceResolver = courseReferenceResolver;
    }

    public LearningCapability(ChaoxingClient chaoxingClient, ChaoxingProperties properties,
                              ChaoxingLoginService loginService) {
        this(chaoxingClient, properties, loginService, null);
    }

    /**
     * 执行半自动登录。
     *
     * @return 登录结果消息
     */
    public LearningLoginResult login() {
        if (!properties.isEnabled()) {
            return new LearningLoginResult(
                    "学习通功能未启用，请先设置 CHAOXING_ENABLED=true。", null);
        }
        try {
            ChaoxingLoginChallenge challenge = loginService.login();
            GeneratedMedia qrCode = new GeneratedMedia(
                    GeneratedMediaKind.IMAGE,
                    challenge.qrCodePng(),
                    "chaoxing-login-qr.png",
                    0L,
                    "请使用学习通 App 扫码并在手机上确认登录");
            return new LearningLoginResult(
                    "已生成短时有效的学习通登录二维码，请使用手机上的学习通 App 扫码并确认。"
                            + "确认后直接发送“查课程”或“查待办”；二维码和登录 Cookie 均不会写入磁盘。",
                    qrCode);
        } catch (ChaoxingLoginException exception) {
            return new LearningLoginResult(exception.getMessage(), null);
        }
    }

    /**
     * 检查学习通功能是否启用（不要求 Cookie）。
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 检查能力是否可用。
     */
    public boolean available() {
        boolean clientAvailable = chaoxingClient.isAvailable();
        log.info("learning_capability_available clientAvailable={}", clientAvailable);
        return clientAvailable;
    }

    /**
     * 获取课程列表。
     *
     * @return 格式化的课程列表字符串
     */
    public String getCourseList() {
        return getCourseList("");
    }

    public String getCourseList(String conversationId) {
        String authenticationFailure = authenticationFailure();
        if (authenticationFailure != null) {
            return authenticationFailure;
        }

        try {
            List<CourseDetail> details = chaoxingClient.fetchCourseDetails();
            if (details.isEmpty()) {
                return "未找到任何课程。";
            }
            rememberCourseReferences(conversationId, details);

            StringBuilder sb = new StringBuilder();
            sb.append("📚 你的课程列表：\n\n");

            for (int i = 0; i < details.size(); i++) {
                CourseDetail detail = details.get(i);
                sb.append(String.format("[C%d] %s\n", i + 1, detail.courseName()));
                if (!detail.className().isEmpty()) {
                    sb.append("   班级：").append(detail.className()).append("\n");
                }
                if (!detail.teacher().isEmpty()) {
                    sb.append("   教师：").append(detail.teacher()).append("\n");
                }
                String openingPeriod = openingPeriod(detail);
                if (!openingPeriod.isBlank()) {
                    sb.append("   开设：").append(openingPeriod).append("\n");
                }
                sb.append("\n");
            }
            sb.append("回复例如“查 C1 的任务”或直接说课程名称即可继续。\n");
            return sb.toString();
        } catch (Exception e) {
            return userFailure("learning_get_course_list_failed", "获取课程列表失败，请稍后重试。", e);
        }
    }

    /**
     * 获取课程详情。
     *
     * @return 格式化的课程详情字符串
     */
    public String getCourseDetails() {
        return getCourseDetails("");
    }

    /** Gets all course details or one course selected by C-number/name. */
    public String getCourseDetails(String selector) {
        return getCourseDetails("", selector);
    }

    public String getCourseDetails(String conversationId, String selector) {
        String authenticationFailure = authenticationFailure();
        if (authenticationFailure != null) {
            return authenticationFailure;
        }

        try {
            List<CourseDetail> details = chaoxingClient.fetchCourseDetails();
            if (details.isEmpty()) {
                return "未找到任何课程。";
            }

            CourseSelection selection = selectCourse(details, selector);
            if (selection.error() != null) {
                return selection.error();
            }
            rememberCourseReferences(conversationId, details, selection.detail());
            List<CourseDetail> selectedDetails = selection.detail() == null
                    ? details
                    : List.of(selection.detail());

            StringBuilder sb = new StringBuilder();
            sb.append("📚 你的课程详情：\n\n");

            for (CourseDetail detail : selectedDetails) {
                int index = details.indexOf(detail) + 1;
                sb.append(String.format("[C%d]【%s】\n", index, detail.courseName()));
                sb.append("  班级：").append(detail.className()).append("\n");
                sb.append("  教师：").append(detail.teacher()).append("\n");
                if (!detail.school().isEmpty()) {
                    sb.append("  学校：").append(detail.school()).append("\n");
                }
                String openingPeriod = openingPeriod(detail);
                if (!openingPeriod.isBlank()) {
                    sb.append("  开设时间：").append(openingPeriod).append("\n");
                }
                sb.append("  学生：").append(detail.studentCount()).append("人\n");
                sb.append("  状态：").append(detail.state() == 1 ? "进行中" : "已结束").append("\n");
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return userFailure("learning_get_course_details_failed", "获取课程详情失败，请稍后重试。", e);
        }
    }

    /**
     * 获取指定课程的任务列表。
     *
     * @param courseId 课程 ID
     * @param classId 班级 ID
     * @param enc 加密参数
     * @return 格式化的任务列表字符串
     */
    public String getTaskList(String courseId, String classId, String enc) {
        String authenticationFailure = authenticationFailure();
        if (authenticationFailure != null) {
            return authenticationFailure;
        }

        try {
            List<CourseDetail> details = chaoxingClient.fetchCourseDetails();
            CourseDetail detail = findCourse(details, courseId, classId);
            if (detail == null) {
                return "没有找到对应课程，请先发送“查课程”。";
            }
            String cpi = requireCourseCpi(detail);
            String effectiveEnc = detail.enc().isBlank() ? normalizeIdentifier(enc) : detail.enc();
            List<CourseTask> tasks = chaoxingClient.fetchTasks(
                    detail.courseId(), detail.classId(), cpi, effectiveEnc);
            return formatTaskList(detail.courseName(), tasks);
        } catch (Exception e) {
            return userFailure("learning_get_task_list_failed", "获取任务列表失败，请稍后重试。", e);
        }
    }

    /** Resolves a course name or C-number before querying its tasks. */
    public String getTaskList(String selector) {
        String authenticationFailure = authenticationFailure();
        if (authenticationFailure != null) {
            return authenticationFailure;
        }
        try {
            List<CourseDetail> details = chaoxingClient.fetchCourseDetails();
            CourseSelection selection = selectCourse(details, selector);
            if (selection.error() != null) {
                return selection.error();
            }
            if (selection.detail() == null) {
                return "请指定课程名称或 C1 形式的课程引用。";
            }
            CourseDetail detail = selection.detail();
            String cpi = requireCourseCpi(detail);
            List<CourseTask> tasks = chaoxingClient.fetchTasks(
                    detail.courseId(), detail.classId(), cpi, detail.enc());
            return formatTaskList(detail.courseName(), tasks);
        } catch (Exception exception) {
            return userFailure(
                    "learning_get_selected_task_list_failed",
                    "获取课程任务失败，请稍后重试。",
                    exception);
        }
    }

    /**
     * 检查所有课程的未完成任务。
     *
     * @return 格式化的未完成任务字符串
     */
    public String checkAllPendingTasks() {
        String authenticationFailure = authenticationFailure();
        if (authenticationFailure != null) {
            return authenticationFailure;
        }

        try {
            List<CourseDetail> courseDetails = chaoxingClient.fetchCourseDetails();
            if (courseDetails.isEmpty()) {
                return "未找到任何课程。";
            }

            StringBuilder result = new StringBuilder("📝 未完成任务汇总：\n\n");
            int pendingCount = 0;
            int failedCourses = 0;
            for (int courseIndex = 0; courseIndex < courseDetails.size(); courseIndex++) {
                CourseDetail detail = courseDetails.get(courseIndex);
                List<CourseTask> tasks;
                try {
                    tasks = chaoxingClient.fetchTasks(
                            detail.courseId(), detail.classId(), requireCourseCpi(detail), detail.enc());
                } catch (Exception exception) {
                    failedCourses++;
                    log.warn("learning_check_pending_course_failed courseName={}",
                            detail.courseName(), exception);
                    continue;
                }
                boolean courseHeadingWritten = false;
                for (CourseTask task : tasks) {
                    if (task.isCompleted()) {
                        continue;
                    }
                    if (pendingCount >= MAX_PENDING_TASKS) {
                        result.append("\n结果已达到 ").append(MAX_PENDING_TASKS).append(" 条上限。\n");
                        return result.toString();
                    }
                    if (!courseHeadingWritten) {
                        result.append("【").append(detail.courseName()).append("】\n");
                        courseHeadingWritten = true;
                    }
                    result.append(task.isPending() ? "⏳ " : "❔ ")
                            .append(task.taskName())
                            .append("（")
                            .append(task.completionStatus().displayName())
                            .append("）\n");
                    pendingCount++;
                }
            }
            if (pendingCount == 0) {
                if (failedCourses > 0) {
                    return "未能完整检查所有课程：有 " + failedCourses
                            + " 门课程暂时无法查询，请稍后重试。";
                }
                return "没有发现未完成任务。";
            }
            if (failedCourses > 0) {
                result.append("\n另有 ").append(failedCourses)
                        .append(" 门课程暂时无法查询，以上结果可能不完整，请稍后重试。\n");
            }
            return result.toString();
        } catch (Exception e) {
            return userFailure(
                    "learning_check_all_pending_tasks_failed",
                    "检查未完成任务失败，请稍后重试。",
                    e);
        }
    }

    private static String formatTaskList(String courseName, List<CourseTask> tasks) {
        if (tasks.isEmpty()) {
            return courseName.isBlank() ? "该课程暂无任务。" : "《" + courseName + "》暂无任务。";
        }
        StringBuilder result = new StringBuilder("📝 ");
        if (!courseName.isBlank()) {
            result.append("《").append(courseName).append("》");
        }
        result.append("任务列表：\n\n");
        int completedCount = 0;
        int pendingCount = 0;
        int visibleTasks = Math.min(tasks.size(), MAX_VISIBLE_TASKS);
        for (int index = 0; index < visibleTasks; index++) {
            CourseTask task = tasks.get(index);
            result.append(task.isCompleted() ? "✅ " : "⏳ ")
                    .append(task.taskName()).append("\n")
                    .append("   类型：").append(task.taskType().displayName()).append("\n")
                    .append("   状态：").append(task.completionStatus().displayName()).append("\n");
            if (task.deadline() != null) {
                result.append("   截止：").append(formatDeadline(task.deadline())).append("\n");
            }
            result.append("\n");
            if (task.isCompleted()) {
                completedCount++;
            } else {
                pendingCount++;
            }
        }
        result.append(String.format("📊 统计：%d 已完成，%d 未完成或状态未知", completedCount, pendingCount));
        if (tasks.size() > visibleTasks) {
            result.append("\n仅显示前 ").append(visibleTasks).append(" 个任务。");
        }
        return result.toString();
    }

    private static CourseSelection selectCourse(List<CourseDetail> details, String selector) {
        if (selector == null || selector.isBlank()) {
            return new CourseSelection(null, null);
        }
        String normalized = selector.strip();
        if (normalized.toUpperCase(Locale.ROOT).matches("C[1-9][0-9]{0,2}")) {
            int index = Integer.parseInt(normalized.substring(1)) - 1;
            if (index < 0 || index >= details.size()) {
                return new CourseSelection(null, "课程引用不存在，请重新发送“查课程”。");
            }
            return new CourseSelection(details.get(index), null);
        }
        if (normalized.toUpperCase(Locale.ROOT).startsWith("C")
                && normalized.substring(1).chars().allMatch(Character::isDigit)) {
            return new CourseSelection(null, "课程引用格式无效，请重新发送“查课程”。");
        }
        List<CourseDetail> matches = details.stream()
                .filter(detail -> detail.courseName().equalsIgnoreCase(normalized)
                        || detail.courseName().contains(normalized)
                        || normalized.contains(detail.courseName()))
                .toList();
        if (matches.isEmpty()) {
            return new CourseSelection(null, "没有找到课程“" + normalized + "”，请重新发送“查课程”。");
        }
        if (matches.size() > 1) {
            return new CourseSelection(null, "课程名称匹配到多门课程，请使用 C1 形式的课程引用。");
        }
        return new CourseSelection(matches.get(0), null);
    }

    private static String requireCourseCpi(CourseDetail detail) {
        if (detail.cpi().isBlank()) {
            throw new ChaoxingLoginException("课程数据缺少任务查询标识，请重新扫码登录后再试。");
        }
        return detail.cpi().strip();
    }

    private static CourseDetail findCourse(
            List<CourseDetail> details,
            String courseId,
            String classId) {
        String normalizedCourseId = normalizeIdentifier(courseId);
        String normalizedClassId = normalizeIdentifier(classId);
        return details.stream()
                .filter(detail -> detail.courseId().equals(normalizedCourseId)
                        && detail.classId().equals(normalizedClassId))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeIdentifier(String value) {
        return value == null ? "" : value.strip();
    }

    private static String formatDeadline(Instant deadline) {
        return TASK_DEADLINE_FORMATTER.format(deadline);
    }

    private static String openingPeriod(CourseDetail detail) {
        String beginDate = displayDate(detail.beginDate());
        String endDate = displayDate(detail.endDate());
        if (!beginDate.isBlank() && !endDate.isBlank()) {
            return beginDate + " 至 " + endDate;
        }
        if (!beginDate.isBlank()) {
            return "自 " + beginDate;
        }
        return endDate.isBlank() ? "" : "至 " + endDate;
    }

    private static String displayDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = DATE_PREFIX_PATTERN.matcher(value.strip());
        return matcher.find() ? matcher.group(1) : value.strip();
    }

    private String userFailure(String logCode, String fallback, Exception exception) {
        if (exception instanceof ChaoxingLoginException) {
            return exception.getMessage();
        }
        log.error(logCode, exception);
        return fallback;
    }

    private record CourseSelection(CourseDetail detail, String error) {
    }

    private String authenticationFailure() {
        if (!properties.isEnabled()) {
            return "学习通功能未启用，请先设置 CHAOXING_ENABLED=true。";
        }
        try {
            ChaoxingLoginStatus status = chaoxingClient.refreshLoginStatus();
            return switch (status) {
                case AUTHENTICATED -> null;
                case WAITING_SCAN -> "登录二维码尚未扫码，请用学习通 App 扫描刚才收到的二维码。";
                case WAITING_CONFIRMATION -> "已扫码，请在手机上的学习通 App 中确认登录后再次发送“查课程”。";
                case EXPIRED -> "登录二维码已过期，请发送“登录学习通”获取新二维码。";
                case REJECTED -> "本次扫码登录已取消或被拒绝，请重新发送“登录学习通”。";
                case NOT_STARTED -> "尚未登录学习通，请先发送“登录学习通”获取二维码。";
                case UNAVAILABLE -> "学习通功能当前不可用。";
            };
        } catch (ChaoxingLoginException exception) {
            log.warn("learning_login_status_failed code=upstream_or_response_invalid");
            return exception.getMessage();
        }
    }

    /**
     * Fetches all pending course tasks across all courses, returning structured data
     * for use by {@link PendingItemAdapter} instead of a formatted string.
     *
     * <p>Individual course failures are logged but do not prevent other courses
     * from being returned. Authentication and all-course failures are surfaced to
     * the pending-item aggregator so they cannot be mistaken for an empty list.</p>
     *
     * @return enriched course tasks with their course name, never null
     */
    public List<EnrichedCourseTask> fetchAllPendingCourseTasks() {
        String authFailure = authenticationFailure();
        if (authFailure != null) {
            log.warn("learning_fetch_all_pending_tasks_skipped reason={}", authFailure);
            throw new PendingItemSourceException(authFailure);
        }
        List<CourseDetail> courseDetails;
        try {
            courseDetails = chaoxingClient.fetchCourseDetails();
        } catch (Exception e) {
            log.error("learning_fetch_course_details_failed", e);
            throw new PendingItemSourceException(
                    "学习通未完成任务暂时无法查询，请稍后重试。", e);
        }
        if (courseDetails.isEmpty()) {
            return List.of();
        }
        List<EnrichedCourseTask> result = new ArrayList<>();
        int failedCourses = 0;
        for (int i = 0; i < courseDetails.size(); i++) {
            CourseDetail detail = courseDetails.get(i);
            try {
                List<CourseTask> tasks = chaoxingClient.fetchTasks(
                        detail.courseId(), detail.classId(), requireCourseCpi(detail), detail.enc());
                for (CourseTask task : tasks) {
                    if (!task.isCompleted() && result.size() < MAX_PENDING_TASKS) {
                        result.add(new EnrichedCourseTask(task, detail.courseName()));
                    }
                }
            } catch (Exception e) {
                failedCourses++;
                log.warn("learning_fetch_tasks_for_course_failed courseId={} courseName={}",
                        detail.courseId(), detail.courseName(), e);
                // Continue with next course — single failure does not hide all results
            }
        }
        if (failedCourses > 0) {
            log.warn("learning_fetch_all_pending_tasks_partial failedCourses={} returnedTasks={}",
                    failedCourses, result.size());
            if (failedCourses == courseDetails.size()) {
                throw new PendingItemSourceException(
                        "学习通未完成任务暂时无法查询，请稍后重试。",
                        new IllegalStateException("all course task requests failed"));
            }
        }
        return result;
    }

    /** A {@link CourseTask} with its parent course name for display. */
    public record EnrichedCourseTask(CourseTask task, String courseName) {
        public EnrichedCourseTask {
            java.util.Objects.requireNonNull(task, "task must not be null");
            courseName = courseName != null ? courseName.strip() : "";
        }
    }

    public void clearConversation(String conversationId) {
        if (courseReferenceResolver != null) {
            courseReferenceResolver.clear(conversationId);
        }
    }

    private void rememberCourseReferences(String conversationId, List<CourseDetail> details) {
        if (courseReferenceResolver != null) {
            courseReferenceResolver.remember(
                    conversationId,
                    details,
                    null,
                    properties.getEnc());
        }
    }

    private void rememberCourseReferences(
            String conversationId,
            List<CourseDetail> details,
            CourseDetail selectedDetail) {
        if (courseReferenceResolver != null) {
            courseReferenceResolver.remember(
                    conversationId,
                    details,
                    selectedDetail,
                    properties.getEnc());
        }
    }
}
