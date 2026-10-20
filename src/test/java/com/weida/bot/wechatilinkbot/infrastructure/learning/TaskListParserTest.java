package com.weida.bot.wechatilinkbot.infrastructure.learning;

import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.CourseTask.CompletionStatus;
import com.weida.bot.wechatilinkbot.model.CourseTask.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务列表解析器测试。
 * 覆盖：正常样本、空页面、缺ID、缺标题、未知任务类型、无效截止时间、重复节点、页面选择器变化。
 */
class TaskListParserTest {

    private TaskListParser parser;

    @BeforeEach
    void setUp() {
        parser = new TaskListParser();
    }

    @Test
    void parseTaskListWithNewModel() {
        String html = """
            <html>
            <body>
            <div class="main task-list">
                <div class="bottomList">
                    <ul>
                        <li data="/task?courseId=200000001&amp;classId=300000001&amp;workId=50000001">
                            <div class="tag work-icon icon-zy-g"></div>
                            <div class="right-content">
                                <p class="overHidden2 fl">主观题作业（第四章）</p>
                                <div class="clear"></div>
                                <p class="status fl">已完成</p>
                            </div>
                        </li>
                        <li data="/task?courseId=200000001&amp;classId=300000001&amp;workId=50000002">
                            <div class="tag work-icon icon-zy-g"></div>
                            <div class="right-content">
                                <p class="overHidden2 fl">第三章作业</p>
                                <div class="clear"></div>
                                <p class="status fl">未完成</p>
                            </div>
                        </li>
                    </ul>
                </div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "200000001", "chaoxing");

        assertThat(tasks).hasSize(2);

        CourseTask first = tasks.get(0);
        assertThat(first.taskId()).isEqualTo("50000001");
        assertThat(first.taskName()).isEqualTo("主观题作业（第四章）");
        assertThat(first.taskType()).isEqualTo(TaskType.HOMEWORK);
        assertThat(first.completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(first.isCompleted()).isTrue();
        assertThat(first.courseId()).isEqualTo("200000001");
        assertThat(first.source()).isEqualTo("chaoxing");
        assertThat(first.deduplicationKey()).isEqualTo("chaoxing:200000001:50000001");

        CourseTask second = tasks.get(1);
        assertThat(second.taskId()).isEqualTo("50000002");
        assertThat(second.taskType()).isEqualTo(TaskType.HOMEWORK);
        assertThat(second.completionStatus()).isEqualTo(CompletionStatus.PENDING);
        assertThat(second.isPending()).isTrue();
    }

    @Test
    void parseTaskListWithDeadline() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">第一章 视频学习</div>
                <div class="task-type">视频</div>
                <div class="task-deadline">2026-08-15 23:59:59</div>
                <div class="task-status">已完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).hasSize(1);
        CourseTask task = tasks.get(0);
        assertThat(task.taskType()).isEqualTo(TaskType.CHAPTER);
        assertThat(task.deadline()).isNotNull();
        assertThat(task.parseNote()).isEmpty();
    }

    @Test
    void parseTaskListWithAbsoluteDeadlineWithoutSeconds() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">第七章作业</div>
                <div class="task-deadline">2026-08-15 23:59</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).singleElement().satisfies(task ->
                assertThat(task.deadline()).isEqualTo(
                        Instant.parse("2026-08-15T15:59:00Z")));
    }

    @Test
    void parseTaskListWithRelativeRemainingDeadline() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-08-07T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        TaskListParser fixedParser = new TaskListParser(fixedClock);
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">剩余时间作业</div>
                <div class="task-deadline">剩余2小时30分钟</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = fixedParser.parseTaskList(html, "course1", "test");

        assertThat(tasks).singleElement().satisfies(task ->
                assertThat(task.deadline()).isEqualTo(
                        Instant.parse("2026-08-07T04:30:00Z")));
    }

    @Test
    void parseTaskListWithManualDeadlineTreatsAsNoDeadline() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">无截止作业</div>
                <div class="task-deadline">手动结束</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task.deadline()).isNull();
            assertThat(task.parseNote()).isEmpty();
        });
    }

    @Test
    void parseTaskListWithInvalidDeadline() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">作业</div>
                <div class="task-type">作业</div>
                <div class="task-deadline">无效时间格式</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).hasSize(1);
        CourseTask task = tasks.get(0);
        assertThat(task.deadline()).isNull();
        assertThat(task.parseNote()).contains("无效的截止时间格式");
    }

    @Test
    void parseTaskListWithUnknownTaskType() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-title">自定义任务</div>
                <div class="task-type">直播</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).hasSize(1);
        CourseTask task = tasks.get(0);
        assertThat(task.taskType()).isEqualTo(TaskType.UNKNOWN);
        assertThat(task.parseNote()).contains("未识别的任务类型");
    }

    @Test
    void parseTaskListWithMissingTaskId() {
        String html = """
            <html>
            <body>
            <div class="task-item">
                <div class="task-title">无ID任务</div>
                <div class="task-type">作业</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseTaskListWithMissingTitle() {
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <div class="task-type">作业</div>
                <div class="task-status">未完成</div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseEmptyTaskList() {
        String html = """
            <html>
            <body>
            <div class="main task-list">
                <div class="bottomList">
                    <ul></ul>
                </div>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html);

        assertThat(tasks).isEmpty();
    }

    @Test
    void parseNullInput() {
        List<CourseTask> tasks = parser.parseTaskList(null);
        assertThat(tasks).isEmpty();
    }

    @Test
    void parseBlankInput() {
        List<CourseTask> tasks = parser.parseTaskList("   ");
        assertThat(tasks).isEmpty();
    }

    @Test
    void parseInvalidHtml() {
        List<CourseTask> tasks = parser.parseTaskList("这不是HTML");
        assertThat(tasks).isEmpty();
    }

    @Test
    void parseDuplicateTasks() {
        String html = """
            <html>
            <body>
            <div class="bottomList">
                <ul>
                    <li data="/task?courseId=200000001&amp;workId=50000001">
                        <p class="overHidden2">作业一</p>
                        <p class="status">已完成</p>
                    </li>
                    <li data="/task?courseId=200000001&amp;workId=50000001">
                        <p class="overHidden2">作业一（重复）</p>
                        <p class="status">未完成</p>
                    </li>
                </ul>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "200000001", "test");

        // 解析器返回两个，去重由 Deduplicator 处理
        assertThat(tasks).hasSize(2);

        // 使用去重工具
        List<CourseTask> deduplicated = LearningDeduplicator.deduplicateTasks(tasks);
        assertThat(deduplicated).hasSize(1);
        assertThat(deduplicated.get(0).taskName()).isEqualTo("作业一");
    }

    @Test
    void parseTaskListWithAlternativeSelectors() {
        // 测试备用选择器
        String html = """
            <html>
            <body>
            <div class="task-item" data-taskid="task-001">
                <p class="title">第一章学习</p>
                <span class="task-type">章节</span>
                <span class="task-status">已完成</span>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "course1", "test");

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskName()).isEqualTo("第一章学习");
        assertThat(tasks.get(0).taskType()).isEqualTo(TaskType.CHAPTER);
    }

    @Test
    void infersCompletionStatusFromWorkIconWhenStatusTextIsMissing() {
        String html = """
            <html>
            <body>
            <div class="bottomList">
                <ul>
                    <li data="/task?courseId=200000001&amp;workId=50000001">
                        <div class="tag work-icon icon-zy-g"></div>
                        <div class="right-content">
                            <p class="overHidden2 fl">已完成的作业</p>
                        </div>
                    </li>
                    <li data="/task?courseId=200000001&amp;workId=50000002">
                        <div class="tag work-icon icon-zy"></div>
                        <div class="right-content">
                            <p class="overHidden2 fl">未交的作业</p>
                        </div>
                    </li>
                </ul>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "200000001", "chaoxing");

        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).completionStatus()).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(tasks.get(0).isCompleted()).isTrue();
        assertThat(tasks.get(1).completionStatus()).isEqualTo(CompletionStatus.PENDING);
        assertThat(tasks.get(1).isPending()).isTrue();
    }

    @Test
    void readsStatusFromNewStuStatusLayout() {
        String html = """
            <html>
            <body>
            <div class="bottomList">
                <ul>
                    <li data="/task?courseId=200000001&amp;workId=50000001">
                        <div class="stuStatus">
                            <span class="statusbg">未交</span>
                        </div>
                        <div class="right-content">
                            <p class="overHidden2 fl">新布局作业</p>
                        </div>
                    </li>
                </ul>
            </div>
            </body>
            </html>
            """;

        List<CourseTask> tasks = parser.parseTaskList(html, "200000001", "chaoxing");

        assertThat(tasks).singleElement().satisfies(task -> {
            assertThat(task.taskName()).isEqualTo("新布局作业");
            assertThat(task.completionStatus()).isEqualTo(CompletionStatus.PENDING);
        });
    }

    @Test
    void extractProgress() {
        String html = """
            <html>
            <body>
            <div class="work-progress-box">
                <div class="progressWrap"><span class="progressing" style="width:100.0%"></span></div>
                <span>5/5</span>
            </div>
            </body>
            </html>
            """;

        String progress = parser.extractProgress(html);

        assertThat(progress).isEqualTo("5/5");
    }

    @Test
    void extractProgressWhenMissing() {
        String html = "<html><body></body></html>";

        String progress = parser.extractProgress(html);

        assertThat(progress).isNull();
    }

    @Test
    void taskTypeNormalization() {
        assertThat(TaskType.fromRawText("作业")).isEqualTo(TaskType.HOMEWORK);
        assertThat(TaskType.fromRawText("练习")).isEqualTo(TaskType.HOMEWORK);
        assertThat(TaskType.fromRawText("work")).isEqualTo(TaskType.HOMEWORK);
        assertThat(TaskType.fromRawText("视频")).isEqualTo(TaskType.CHAPTER);
        assertThat(TaskType.fromRawText("章节")).isEqualTo(TaskType.CHAPTER);
        assertThat(TaskType.fromRawText("考试")).isEqualTo(TaskType.EXAM);
        assertThat(TaskType.fromRawText("测试")).isEqualTo(TaskType.EXAM);
        assertThat(TaskType.fromRawText("exam")).isEqualTo(TaskType.EXAM);
        assertThat(TaskType.fromRawText("直播")).isEqualTo(TaskType.UNKNOWN);
        assertThat(TaskType.fromRawText(null)).isEqualTo(TaskType.UNKNOWN);
        assertThat(TaskType.fromRawText("")).isEqualTo(TaskType.UNKNOWN);
    }

    @Test
    void completionStatusNormalization() {
        assertThat(CompletionStatus.fromRawText("已完成")).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(CompletionStatus.fromRawText("完成")).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(CompletionStatus.fromRawText("done")).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(CompletionStatus.fromRawText("未完成")).isEqualTo(CompletionStatus.PENDING);
        assertThat(CompletionStatus.fromRawText("待完成")).isEqualTo(CompletionStatus.PENDING);
        assertThat(CompletionStatus.fromRawText("进行中")).isEqualTo(CompletionStatus.PENDING);
        assertThat(CompletionStatus.fromRawText("已过期")).isEqualTo(CompletionStatus.PENDING);
        assertThat(CompletionStatus.fromRawText("未通过")).isEqualTo(CompletionStatus.PENDING);
        assertThat(CompletionStatus.fromRawText("待批阅")).isEqualTo(CompletionStatus.COMPLETED);
        assertThat(CompletionStatus.fromRawText(null)).isEqualTo(CompletionStatus.UNKNOWN);
    }
}
