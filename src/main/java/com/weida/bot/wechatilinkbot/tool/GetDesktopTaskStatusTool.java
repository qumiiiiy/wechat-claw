package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GET_DESKTOP_TASK_STATUS;

import com.weida.bot.wechatilinkbot.capability.DesktopTaskCapability;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.tool.input.DesktopTaskIdInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Returns one task's actual persisted status to its owner. */
@Order(72)
@Component
@ConditionalOnBean(DesktopTaskCapability.class)
public class GetDesktopTaskStatusTool implements AgentTool<DesktopTaskIdInput> {

    private final DesktopTaskCapability taskCapability;

    public GetDesktopTaskStatusTool(DesktopTaskCapability taskCapability) {
        this.taskCapability = taskCapability;
    }

    @Override
    public String name() {
        return GET_DESKTOP_TASK_STATUS;
    }

    @Override
    public String description() {
        return "查询当前用户此前创建的电脑任务状态。";
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
        DesktopTask task = taskCapability.getStatus(input.taskId(), context.conversationId());
        String detail = task.resultSummary() == null ? "等待用户确认，尚未执行" : task.resultSummary();
        return new ToolExecution("任务 " + task.taskId() + " 当前状态：" + task.status() + "。" + detail, null);
    }
}
