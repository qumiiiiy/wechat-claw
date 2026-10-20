package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.COMPLETE_TODO;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.tool.input.TodoIdInput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Marks one pending todo as completed, accepting natural references. */
@Order(83)
@Component
@ConditionalOnBean(StudentTodoCapability.class)
public class CompleteTodoTool implements AgentTool<TodoIdInput> {

    private final StudentTodoCapability capability;
    private final ReminderState reminderState;
    private final PendingItemReference reference;
    private final PendingItemService pendingItemService;

    public CompleteTodoTool(
            StudentTodoCapability capability,
            ReminderState reminderState,
            PendingItemReference reference,
            PendingItemService pendingItemService) {
        this.capability = capability;
        this.reminderState = reminderState;
        this.reference = reference;
        this.pendingItemService = pendingItemService;
    }

    @Override
    public String name() {
        return COMPLETE_TODO;
    }

    @Override
    public String description() {
        return "Mark a todo as completed. "
                + "MUST call when user says「完成」「做完了」「交了」「搞定」. "
                + "The user can refer to items by sequence number (第2项, 第一个, 刚才的). "
                + "If user says completed but the reference is ambiguous, call list_todos first.";
    }

    @Override
    public Class<TodoIdInput> inputType() {
        return TodoIdInput.class;
    }

    @Override
    public boolean available() { return true; }

    @Override
    public boolean requiresExplicitIntent() { return true; }

    @Override
    public boolean producesMedia() { return false; }

    @Override
    public boolean returnDirect() { return true; }

    @Override
    public ToolExecution execute(TodoIdInput input, ToolContext context) {
        String rawRef = input.todoId();
        PendingItemReference.Resolution resolved =
                reference.resolve(context.conversationId(), rawRef);
        if (resolved.isError()) {
            throw new IllegalArgumentException(resolved.error());
        }
        // Source check: only PERSONAL items can be modified
        PendingItemSource source = resolved.source() != null
                ? resolved.source()
                : reference.sourceOf(context.conversationId(), resolved.internalId());
        if (source != null && source != PendingItemSource.PERSONAL) {
            throw new IllegalArgumentException(
                    "该待办来自" + sourceLabel(source) + "，请直接在" + sourceLabel(source) + "中处理。");
        }
        StudentTodo existing = capability.findById(
                resolved.internalId(), context.conversationId());
        if (existing == null || existing.status() != StudentTodoStatus.PENDING) {
            throw new IllegalArgumentException(
                    "未找到该待办，可能已被取消或已完成。"
                            + "请使用 list_todos 查看最新待办列表，确认待办存在后再试。");
        }
        StudentTodo completed = capability.completeTodo(
                resolved.internalId(), context.conversationId());
        reference.markLastOperation(context.conversationId(), completed.id());
        pendingItemService.invalidate(context.conversationId());
        var sb = new StringBuilder("✅ 待办「").append(completed.title())
                .append("」已标记完成。");
        var flags = reminderState.consume(context.conversationId());
        if (!flags.isEmpty()) {
            sb.append(flags.format());
        }
        return new ToolExecution(sb.toString(), null);
    }

    private static String sourceLabel(PendingItemSource source) {
        return switch (source) {
            case EMAIL -> "邮箱";
            case COURSE -> "学习通";
            default -> "其他来源";
        };
    }
}
