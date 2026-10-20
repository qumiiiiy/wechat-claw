package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.REQUEST_OPEN_URL;

import com.weida.bot.wechatilinkbot.capability.DesktopTaskCapability;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.tool.input.OpenUrlTaskInput;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates, but never executes, one user-confirmed local browser task. */
@Order(70)
@Component
@ConditionalOnBean(DesktopTaskCapability.class)
public class RequestOpenUrlTool implements AgentTool<OpenUrlTaskInput> {

    private final DesktopTaskCapability taskCapability;

    public RequestOpenUrlTool(DesktopTaskCapability taskCapability) {
        this.taskCapability = taskCapability;
    }

    @Override
    public String name() {
        return REQUEST_OPEN_URL;
    }

    @Override
    public String description() {
        return "为用户明确提供的 HTTPS 网址创建本机打开任务。只创建待确认任务，绝不直接打开浏览器；"
                + "网址缺失时先向用户索取完整 https:// URL。若用户明确要求打开 QQ 邮箱，使用固定网址 "
                + "https://mail.qq.com/。";
    }

    @Override
    public Class<OpenUrlTaskInput> inputType() {
        return OpenUrlTaskInput.class;
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
    public ToolExecution execute(OpenUrlTaskInput input, ToolContext context) {
        DesktopTask task = taskCapability.createOpenUrlDraft(context.conversationId(), input.url());
        long seconds = Duration.between(Instant.now(), task.approvalExpiresAt()).toSeconds();
        return new ToolExecution(
                "任务 " + task.taskId() + " 已创建。计划：在本机默认浏览器打开 "
                        + java.net.URI.create(task.url()).getHost()
                        + "。如确认执行，请下一条消息单独回复“确认 " + task.taskId()
                        + "”；任务将在约 " + Math.max(1, seconds) + " 秒后过期。",
                null);
    }
}
