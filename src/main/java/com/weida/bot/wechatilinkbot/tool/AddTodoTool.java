package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.ADD_TODO;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.tool.input.AddTodoInput;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates a new pending todo for the current conversation. */
@Order(82)
@Component
@ConditionalOnBean(StudentTodoCapability.class)
public class AddTodoTool implements AgentTool<AddTodoInput> {

    private static final DateTimeFormatter DUE_FMT =
            DateTimeFormatter.ofPattern("M月d日 HH:mm")
                    .withZone(StudentTodoCapability.SHANGHAI);

    private final StudentTodoCapability capability;
    private final ReminderState reminderState;
    private final PendingItemReference reference;
    private final PendingItemService pendingItemService;

    public AddTodoTool(
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
        return ADD_TODO;
    }

    @Override
    public String description() {
        return "Create a todo reminder for the user. "
                + "title: extract ONLY the task content, strip「提醒我」「帮我记」etc. "
                + "dueTimeStr: MUST be explicit date+time like「明天18:00」「8月5日09:00」. "
                + "A bare time such as「17:00」is invalid; ask for the date. "
                + "DO NOT call if time is vague. NEVER guess a time.";
    }

    @Override
    public Class<AddTodoInput> inputType() {
        return AddTodoInput.class;
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
    public ToolExecution execute(AddTodoInput input, ToolContext context) {
        StudentTodo todo = capability.createTodo(
                context.conversationId(),
                input.title(),
                input.dueTimeStr());
        reference.markLastOperation(context.conversationId(), todo.id());
        pendingItemService.invalidate(context.conversationId());
        var sb = new StringBuilder("✅ 已创建待办「")
                .append(todo.title()).append("」")
                .append("，截止 ").append(DUE_FMT.format(todo.dueTime())).append("。");
        var flags = reminderState.consume(context.conversationId());
        if (!flags.isEmpty()) {
            sb.append(flags.format());
        }
        return new ToolExecution(sb.toString(), null);
    }
}
