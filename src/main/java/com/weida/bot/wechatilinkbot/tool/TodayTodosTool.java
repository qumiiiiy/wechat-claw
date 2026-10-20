package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.TODAY_TODOS;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Lists pending items due today with natural numbering. */
@Order(81)
@Component
@ConditionalOnBean(com.weida.bot.wechatilinkbot.capability.StudentTodoCapability.class)
public class TodayTodosTool implements AgentTool<NoArgumentsToolInput> {

    private final PendingItemService pendingItemService;
    private final ReminderState reminderState;
    private final PendingItemReference reference;

    public TodayTodosTool(
            PendingItemService pendingItemService,
            ReminderState reminderState,
            PendingItemReference reference) {
        this.pendingItemService = pendingItemService;
        this.reminderState = reminderState;
        this.reference = reference;
    }

    @Override
    public String name() {
        return TODAY_TODOS;
    }

    @Override
    public String description() {
        return "List todos due today. "
                + "MUST call this tool when user asks「今天截止」「今日到期」「今天有什么」「today deadline」. "
                + "This tool takes no parameters (ignore the input fields).";
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
        var result = pendingItemService.listTodayWithStatus(context.conversationId());
        List<PendingItem> items = result.items();
        if (items.isEmpty()) {
            if (!result.warnings().isEmpty()) {
                return new ToolExecution(
                        "📅 今天没有已查询到的截止待办。\n\n"
                                + formatWarnings(result.warnings()), null);
            }
            return new ToolExecution("📅 今天没有截止的待办。", null);
        }
        reference.remember(context.conversationId(), items);
        var sb = new StringBuilder();
        sb.append("📅 今日待办（共 ").append(items.size()).append(" 项）\n");
        for (PendingItem item : items) {
            sb.append("  ").append(item.formatListItem()).append('\n');
        }
        var flags = reminderState.consume(context.conversationId());
        if (!flags.isEmpty()) {
            sb.append(flags.format());
        }
        if (!result.warnings().isEmpty()) {
            sb.append("\n⚠️ 部分来源未能查询：\n");
            result.warnings().forEach(warning -> sb.append("- ").append(warning).append('\n'));
        }
        return new ToolExecution(sb.toString(), null);
    }

    private static String formatWarnings(List<String> warnings) {
        StringBuilder builder = new StringBuilder("⚠️ 部分来源未能查询：\n");
        warnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
        return builder.toString().strip();
    }
}
