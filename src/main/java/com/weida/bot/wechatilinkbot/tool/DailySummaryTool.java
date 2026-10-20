package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.DAILY_SUMMARY;

import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.DailySummary;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Generates a today's-priorities summary for the current conversation. */
@Order(78)
@Component
@ConditionalOnBean(StudentTodoCapability.class)
public class DailySummaryTool implements AgentTool<NoArgumentsToolInput> {

    private static final int DEFAULT_LIMIT = 20;

    private final StudentTodoCapability capability;

    public DailySummaryTool(StudentTodoCapability capability) {
        this.capability = capability;
    }

    @Override
    public String name() {
        return DAILY_SUMMARY;
    }

    @Override
    public String description() {
        return "Generate a today's priorities summary（今日要务）. "
                + "MUST call when user asks「今日要务」「每日总结」「今天要做什么」「总结待办」. "
                + "Takes no parameters.";
    }

    @Override
    public Class<NoArgumentsToolInput> inputType() {
        return NoArgumentsToolInput.class;
    }

    @Override
    public boolean available() {
        return true;
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
    public ToolExecution execute(NoArgumentsToolInput input, ToolContext context) {
        DailySummary summary = capability.buildSummary(
                context.conversationId(), DEFAULT_LIMIT);
        return new ToolExecution(summary.format(), null);
    }
}
