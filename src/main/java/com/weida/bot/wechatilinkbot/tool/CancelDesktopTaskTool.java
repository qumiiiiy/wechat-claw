package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.CANCEL_DESKTOP_TASK;

import com.weida.bot.wechatilinkbot.capability.DesktopTaskCapability;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.tool.input.DesktopTaskIdInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Cancels an unexecuted desktop task owned by the current conversation. */
@Order(73)
@Component
@ConditionalOnBean(DesktopTaskCapability.class)
public class CancelDesktopTaskTool implements AgentTool<DesktopTaskIdInput> {

    private final DesktopTaskCapability taskCapability;

    public CancelDesktopTaskTool(DesktopTaskCapability taskCapability) {
        this.taskCapability = taskCapability;
    }

    @Override
    public String name() {
        return CANCEL_DESKTOP_TASK;
    }

    @Override
    public String description() {
        return "取消当前用户尚未执行的电脑任务。任务已执行、已失败或已过期时不得取消。";
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
        DesktopTask task = taskCapability.cancel(input.taskId(), context.conversationId());
        return new ToolExecution("任务 " + task.taskId() + " 已取消，未执行任何电脑操作。", null);
    }
}
