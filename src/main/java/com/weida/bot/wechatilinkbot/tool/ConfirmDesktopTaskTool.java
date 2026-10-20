package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.CONFIRM_DESKTOP_TASK;

import com.weida.bot.wechatilinkbot.capability.DesktopTaskCapability;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.model.DesktopTaskStatus;
import com.weida.bot.wechatilinkbot.tool.input.DesktopTaskIdInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Confirms and executes a server-created desktop task owned by the current user. */
@Order(71)
@Component
@ConditionalOnBean(DesktopTaskCapability.class)
public class ConfirmDesktopTaskTool implements AgentTool<DesktopTaskIdInput> {

    private final DesktopTaskCapability taskCapability;

    public ConfirmDesktopTaskTool(DesktopTaskCapability taskCapability) {
        this.taskCapability = taskCapability;
    }

    @Override
    public String name() {
        return CONFIRM_DESKTOP_TASK;
    }

    @Override
    public String description() {
        return "仅在用户下一条消息明确确认此前的电脑任务并提供任务 ID 时使用。执行结果由工具决定，"
                + "不能根据用户或模型文字宣称任务已完成。";
    }

    @Override
    public Class<DesktopTaskIdInput> inputType() {
        return DesktopTaskIdInput.class;
    }

    @Override
    public boolean available() {
        return taskCapability.available();
    }

    @Override
    public boolean requiresExplicitIntent() {
        return true;
    }

    @Override
    public boolean producesMedia() {
        return false;
    }

    @Override
    public boolean returnDirect() {
        return true;
    }

    @Override
    public ToolExecution execute(DesktopTaskIdInput input, ToolContext context) {
        DesktopTask task = taskCapability.confirm(input.taskId(), context.conversationId());
        if (task.status() == DesktopTaskStatus.SUCCEEDED) {
            return new ToolExecution("任务 " + task.taskId() + " 已完成：" + task.resultSummary(), null);
        }
        return new ToolExecution("任务 " + task.taskId() + " 未完成：" + task.resultSummary(), null);
    }
}
