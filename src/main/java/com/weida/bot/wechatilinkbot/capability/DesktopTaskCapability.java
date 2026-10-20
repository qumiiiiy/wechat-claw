package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutionException;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.model.DesktopTaskResult;
import com.weida.bot.wechatilinkbot.model.DesktopTaskStatus;
import com.weida.bot.wechatilinkbot.persistence.DesktopTaskRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** User-visible two-step capability for opening one validated local HTTPS URL. */
@Component
@ConditionalOnBean(DesktopTaskRepository.class)
@ConditionalOnProperty(prefix = "bot.capability.desktop", name = "enabled", havingValue = "true")
public class DesktopTaskCapability {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopTaskCapability.class);

    private final DesktopTaskRepository taskRepository;
    private final DesktopTaskPolicy taskPolicy;
    private final WindowsDesktopTaskExecutor taskExecutor;
    private final Clock clock;

    @Autowired
    public DesktopTaskCapability(
            DesktopTaskRepository taskRepository,
            DesktopTaskPolicy taskPolicy,
            WindowsDesktopTaskExecutor taskExecutor) {
        this(taskRepository, taskPolicy, taskExecutor, Clock.systemUTC());
    }

    DesktopTaskCapability(
            DesktopTaskRepository taskRepository,
            DesktopTaskPolicy taskPolicy,
            WindowsDesktopTaskExecutor taskExecutor,
            Clock clock) {
        this.taskRepository = taskRepository;
        this.taskPolicy = taskPolicy;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public boolean available() {
        return taskExecutor.available();
    }

    public DesktopTask createOpenUrlDraft(String conversationId, String rawUrl) {
        taskPolicy.validateFeatureEnabled();
        taskPolicy.validateConversation(conversationId);
        URI url = taskPolicy.normalizeAndValidateUrl(rawUrl);
        Instant now = clock.instant();
        DesktopTask task = new DesktopTask(
                "DT-" + UUID.randomUUID(),
                conversationId,
                url.toString(),
                DesktopTaskStatus.WAITING_APPROVAL,
                now,
                now.plus(approvalExpireAfter()),
                null,
                null,
                null,
                null);
        return taskRepository.create(task);
    }

    public DesktopTask confirm(String taskId, String conversationId) {
        DesktopTask task = findOwnedTask(taskId, conversationId);
        task = markTimedOutIfExpired(task);
        taskPolicy.validateApproval(task, conversationId);
        Instant now = clock.instant();
        DesktopTask runningTask = new DesktopTask(
                task.taskId(),
                task.conversationId(),
                task.url(),
                DesktopTaskStatus.RUNNING,
                task.createdAt(),
                task.approvalExpiresAt(),
                now,
                null,
                null,
                null);
        taskRepository.update(runningTask);
        try {
            DesktopTaskResult result = taskExecutor.openUrl(URI.create(runningTask.url()));
            return taskRepository.update(completed(runningTask, DesktopTaskStatus.SUCCEEDED, result));
        } catch (WindowsDesktopTaskExecutionException exception) {
            LOG.error(
                    "desktop_task_execution_failed taskId={} host={} code=open_url_failed",
                    runningTask.taskId(),
                    URI.create(runningTask.url()).getHost(),
                    exception);
            DesktopTaskResult result = DesktopTaskResult.failed("open_url_failed", "本机浏览器未能打开该网址");
            return taskRepository.update(completed(runningTask, DesktopTaskStatus.FAILED, result));
        }
    }

    public DesktopTask getStatus(String taskId, String conversationId) {
        DesktopTask task = findOwnedTask(taskId, conversationId);
        return markTimedOutIfExpired(task);
    }

    public DesktopTask cancel(String taskId, String conversationId) {
        DesktopTask task = findOwnedTask(taskId, conversationId);
        task = markTimedOutIfExpired(task);
        taskPolicy.validateCancellation(task, conversationId);
        Instant now = clock.instant();
        return taskRepository.update(new DesktopTask(
                task.taskId(),
                task.conversationId(),
                task.url(),
                DesktopTaskStatus.CANCELLED,
                task.createdAt(),
                task.approvalExpiresAt(),
                task.approvedAt(),
                now,
                "cancelled",
                "任务已取消，未执行任何电脑操作"));
    }

    private DesktopTask findOwnedTask(String taskId, String conversationId) {
        taskPolicy.validateFeatureEnabled();
        taskPolicy.validateConversation(conversationId);
        DesktopTask task = taskRepository.findById(requireTaskId(taskId))
                .orElseThrow(() -> new DesktopTaskPolicyViolationException("未找到该电脑任务"));
        if (!task.conversationId().equals(conversationId.strip())) {
            throw new DesktopTaskPolicyViolationException("只能操作本人创建的电脑任务");
        }
        return task;
    }

    private DesktopTask markTimedOutIfExpired(DesktopTask task) {
        if (!task.isApprovalExpired(clock.instant())) {
            return task;
        }
        return taskRepository.update(new DesktopTask(
                task.taskId(),
                task.conversationId(),
                task.url(),
                DesktopTaskStatus.TIMED_OUT,
                task.createdAt(),
                task.approvalExpiresAt(),
                task.approvedAt(),
                clock.instant(),
                "approval_expired",
                "任务确认已过期，未执行任何电脑操作"));
    }

    private DesktopTask completed(
            DesktopTask task,
            DesktopTaskStatus status,
            DesktopTaskResult result) {
        return new DesktopTask(
                task.taskId(),
                task.conversationId(),
                task.url(),
                status,
                task.createdAt(),
                task.approvalExpiresAt(),
                task.approvedAt(),
                clock.instant(),
                result.code(),
                result.summary());
    }

    private java.time.Duration approvalExpireAfter() {
        return taskPolicy.approvalExpireAfter();
    }

    private static String requireTaskId(String taskId) {
        if (taskId == null || taskId.isBlank() || taskId.strip().length() > 64) {
            throw new DesktopTaskPolicyViolationException("任务 ID 不正确");
        }
        return taskId.strip();
    }
}
