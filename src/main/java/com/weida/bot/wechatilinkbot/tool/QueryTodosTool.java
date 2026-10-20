package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.QUERY_TODOS;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.TodoFilter;
import com.weida.bot.wechatilinkbot.tool.input.TodoQueryInput;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Queries pending items by dimension with natural numbering. */
@org.springframework.core.annotation.Order(79)
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(
        com.weida.bot.wechatilinkbot.capability.StudentTodoCapability.class)
public class QueryTodosTool implements AgentTool<TodoQueryInput> {

    private final PendingItemService pendingItemService;
    private final ReminderState reminderState;
    private final PendingItemReference reference;

    public QueryTodosTool(
            PendingItemService pendingItemService,
            ReminderState reminderState,
            PendingItemReference reference) {
        this.pendingItemService = pendingItemService;
        this.reminderState = reminderState;
        this.reference = reference;
    }

    @Override
    public String name() {
        return QUERY_TODOS;
    }

    @Override
    public String description() {
        return "Query todos by dimension. "
                + "ALL/TODAY/TOMORROW/WEEK/OVERDUE. "
                + "MUST call when user asks about a specific time range.";
    }

    @Override
    public Class<TodoQueryInput> inputType() {
        return TodoQueryInput.class;
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
    public ToolExecution execute(TodoQueryInput input, ToolContext context) {
        TodoFilter filter = input.filter();
        boolean rangeRequested = !input.from().isBlank() || !input.to().isBlank();
        boolean genericUpcoming = filter == null && !rangeRequested;
        PendingItemService.PendingItemQuery result;
        if (rangeRequested) {
            result = pendingItemService.listRangeWithStatus(
                    context.conversationId(),
                    resolveSources(input),
                    input.from(),
                    input.to());
        } else if (genericUpcoming) {
            result = pendingItemService.listUpcomingWithStatus(
                    context.conversationId(),
                    resolveSources(input));
        } else {
            result = switch (filter) {
                case TODAY -> pendingItemService.listTodayWithStatus(context.conversationId());
                case TOMORROW -> pendingItemService.listTomorrowWithStatus(context.conversationId());
                case WEEK -> pendingItemService.listThisWeekWithStatus(context.conversationId());
                case OVERDUE -> pendingItemService.listOverdueWithStatus(context.conversationId());
                case EMAIL_ONLY -> pendingItemService.listBySourcesWithStatus(
                        context.conversationId(), Set.of(PendingItemSource.EMAIL));
                case COURSE_ONLY -> pendingItemService.listBySourcesWithStatus(
                        context.conversationId(), Set.of(PendingItemSource.COURSE));
                case ALL -> pendingItemService.listAllWithStatus(context.conversationId());
                default -> pendingItemService.listAllWithStatus(context.conversationId());
            };
        }
        List<PendingItem> items = result.items();

        if (items.isEmpty()) {
            String label = switch (filter) {
                case OVERDUE -> "逾期的";
                case TODAY -> "今天";
                case TOMORROW -> "明天";
                case WEEK -> "本周";
                case EMAIL_ONLY -> "邮箱";
                case COURSE_ONLY -> "学习通";
                case ALL -> "";
                default -> "";
            };
            if (genericUpcoming) {
                label = "一个月内";
            }
            String message = result.warnings().isEmpty()
                    ? "没有" + label + "待办。"
                    : "没有已查询到的" + label + "待办。\n\n" + formatWarnings(result.warnings());
            return new ToolExecution(message, null);
        }
        reference.remember(context.conversationId(), items);

        var sb = new StringBuilder();
        sb.append("📋 ").append(rangeRequested ? "按时间过滤的待办"
                : genericUpcoming ? "近期待办（一个月内）" : filterLabel(filter))
                .append("（共 ").append(items.size()).append(" 项）\n");
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

    private static String filterLabel(TodoFilter filter) {
        if (filter == null) {
            return "近期待办";
        }
        return switch (filter) {
            case ALL -> "全部待办";
            case TODAY -> "今日待办";
            case TOMORROW -> "明日待办";
            case WEEK -> "本周待办";
            case OVERDUE -> "逾期待办";
            case EMAIL_ONLY -> "邮箱待办";
            case COURSE_ONLY -> "学习通待办";
            default -> "待办";
        };
    }

    private static Set<PendingItemSource> resolveSources(TodoQueryInput input) {
        String source = input.source();
        if (source != null && !source.isBlank()) {
            return switch (source.strip().toUpperCase(Locale.ROOT)) {
                case "PERSONAL" -> Set.of(PendingItemSource.PERSONAL);
                case "EMAIL" -> Set.of(PendingItemSource.EMAIL);
                case "COURSE" -> Set.of(PendingItemSource.COURSE);
                case "ALL" -> Set.of(PendingItemSource.values());
                default -> throw new IllegalArgumentException("未知待办来源：" + source);
            };
        }
        if (input.filter() == TodoFilter.COURSE_ONLY) {
            return Set.of(PendingItemSource.COURSE);
        }
        if (input.filter() == TodoFilter.EMAIL_ONLY) {
            return Set.of(PendingItemSource.EMAIL);
        }
        return null;
    }
}
