package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.LIST_TODOS;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.tool.input.NoArgumentsToolInput;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Lists all pending items for the current conversation with natural numbering. */
@Order(80)
@Component
@ConditionalOnBean(StudentTodoCapability.class)
public class ListTodosTool implements AgentTool<NoArgumentsToolInput> {

    private final PendingItemService pendingItemService;
    private final ReminderState reminderState;
    private final PendingItemReference reference;

    public ListTodosTool(
            PendingItemService pendingItemService,
            ReminderState reminderState,
            PendingItemReference reference) {
        this.pendingItemService = pendingItemService;
        this.reminderState = reminderState;
        this.reference = reference;
    }

    @Override
    public String name() {
        return LIST_TODOS;
    }

    @Override
    public String description() {
        return "List all pending todos for the current user. "
                + "MUST call this tool when user asks「查待办」「有哪些作业」「我的待办」「还有什么」. "
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
        var result = pendingItemService.listUpcomingWithStatus(
                context.conversationId(), null);
        List<PendingItem> items = result.items();
        if (items.isEmpty()) {
            String warnings = formatWarnings(result.warnings());
            if (!warnings.isBlank()) {
                return new ToolExecution(
                        "📋 当前没有待办事项；但部分来源未能查询，结果不完整。\n\n"
                                + warnings, null);
            }
            return new ToolExecution(
                    "📋 当前没有待办事项。你可以说「提醒我 标题 截止时间」来创建。", null);
        }
        reference.remember(context.conversationId(), items);

        var sb = new StringBuilder();
        sb.append("📋 全部待办（共 ").append(items.size()).append(" 项）\n");
        for (PendingItem item : items) {
            sb.append("  ").append(item.formatListItem());
            if (item.dueTime().isBefore(java.time.Instant.now())) {
                sb.append(" ❗已逾期");
            }
            sb.append('\n');
        }

        var flags = reminderState.consume(context.conversationId());
        if (!flags.isEmpty()) {
            sb.append(flags.format());
        }
        appendWarnings(sb, result.warnings());
        return new ToolExecution(sb.toString(), null);
    }

    private static void appendWarnings(StringBuilder builder, List<String> warnings) {
        if (!warnings.isEmpty()) {
            builder.append("\n⚠️ 部分来源未能查询：\n");
            warnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
        }
    }

    private static String formatWarnings(List<String> warnings) {
        if (warnings.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("⚠️ 部分来源未能查询：\n");
        warnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
        return builder.toString().strip();
    }
}
