package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.MODIFY_TODO;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.tool.input.ModifyTodoInput;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Changes the due time of a pending todo, accepting natural references. */
@Order(85)
@Component
@ConditionalOnBean(StudentTodoCapability.class)
public class ModifyTodoTool implements AgentTool<ModifyTodoInput> {

    private static final DateTimeFormatter DUE_FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    private final StudentTodoCapability capability;
    private final ReminderState reminderState;
    private final PendingItemReference reference;
    private final PendingItemService pendingItemService;

    public ModifyTodoTool(
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
    public String name() { return MODIFY_TODO; }

    @Override
    public String description() {
        return "Change the due time of a pending todo. "
                + "MUST call when user says「推迟」「改期」「改到」「延后」. "
                + "Accepts natural references: 第2项, 第一个, 刚才的.";
    }

    @Override
    public Class<ModifyTodoInput> inputType() { return ModifyTodoInput.class; }

    @Override
    public boolean available() { return true; }

    @Override
    public boolean requiresExplicitIntent() { return true; }

    @Override
    public boolean producesMedia() { return false; }

    @Override
    public boolean returnDirect() { return true; }

    @Override
    public ToolExecution execute(ModifyTodoInput input, ToolContext context) {
        PendingItemReference.Resolution resolved =
                reference.resolve(context.conversationId(), input.todoId());
        if (resolved.isError()) {
            return new ToolExecution(resolved.error(), null);
        }
        PendingItemSource source = resolved.source() != null
                ? resolved.source()
                : reference.sourceOf(context.conversationId(), resolved.internalId());
        if (source != null && source != PendingItemSource.PERSONAL) {
            return new ToolExecution("只能修改个人待办的截止时间，该待办来自"
                    + sourceLabel(source) + "。", null);
        }
        StudentTodo existing = capability.findById(
                resolved.internalId(), context.conversationId());
        if (existing == null || existing.status() != StudentTodoStatus.PENDING) {
            return new ToolExecution(
                    "未找到该待办，可能已被取消或已完成。"
                            + "请使用 list_todos 查看最新待办列表，确认待办存在后再试。",
                    null);
        }
        StudentTodo modified = capability.modifyDueTime(
                resolved.internalId(), context.conversationId(), input.newDueTimeStr());
        reference.markLastOperation(context.conversationId(), modified.id());
        pendingItemService.invalidate(context.conversationId());
        var sb = new StringBuilder("✅ 待办「").append(modified.title())
                .append("」截止时间已改为 ").append(DUE_FMT.format(modified.dueTime()))
                .append("。");
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