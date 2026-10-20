package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.CourseTask;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Adapts Chaoxing course tasks into the unified {@link PendingItem} view.
 *
 * <p>Course tasks are read-only — users cannot complete or cancel them
 * through the bot. Write operations must happen on the learning platform.</p>
 */
@Component
@ConditionalOnBean(LearningCapability.class)
public class CourseTaskPendingAdapter implements PendingItemAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CourseTaskPendingAdapter.class);

    private final LearningCapability learningCapability;

    public CourseTaskPendingAdapter(LearningCapability learningCapability) {
        this.learningCapability = learningCapability;
    }

    @Override
    public PendingItemSource source() {
        return PendingItemSource.COURSE;
    }

    @Override
    public List<PendingItem> fetchItems(String conversationId) {
        List<LearningCapability.EnrichedCourseTask> enriched =
                learningCapability.fetchAllPendingCourseTasks();
        return enriched.stream()
                .map(e -> toPendingItem(e.task(), e.courseName()))
                .toList();
    }

    @Override
    public boolean supportsModify() {
        return false;
    }

    private static PendingItem toPendingItem(CourseTask task, String courseName) {
        String title = courseName.isBlank()
                ? task.taskName()
                : courseName + " - " + task.taskName();
        Instant dueTime = task.deadline() != null
                ? task.deadline()
                : Instant.MAX; // no deadline → sorts to the end
        return new PendingItem(
                0, task.deduplicationKey(), title,
                PendingItemSource.COURSE, StudentTodoStatus.PENDING, dueTime,
                null);
    }
}
