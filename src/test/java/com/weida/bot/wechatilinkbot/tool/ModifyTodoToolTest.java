package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.capability.PendingItemReference;
import com.weida.bot.wechatilinkbot.capability.PersonalTodoPendingAdapter;
import com.weida.bot.wechatilinkbot.capability.PendingItemService;
import com.weida.bot.wechatilinkbot.capability.ReminderState;
import com.weida.bot.wechatilinkbot.capability.StudentTodoCapability;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.persistence.InMemoryStudentTodoMemory;
import com.weida.bot.wechatilinkbot.tool.input.ModifyTodoInput;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("修改待办截止时间")
class ModifyTodoToolTest {

    private static final String CONV = "wechat-ilink|test";
    private static final Instant FIXED = Instant.parse("2026-08-05T02:00:00Z");

    private StudentTodoCapability capability;
    private PendingItemReference reference;
    private ModifyTodoTool tool;
    private ToolContext ctx;
    private String todoId;

    @BeforeEach
    void setUp() {
        capability = new StudentTodoCapability(
                new InMemoryStudentTodoMemory(),
                Clock.fixed(FIXED, StudentTodoCapability.SHANGHAI));
        PendingItemService psvc = new PendingItemService(
                List.of(new PersonalTodoPendingAdapter(capability)),
                Clock.fixed(FIXED, StudentTodoCapability.SHANGHAI));
        reference = new PendingItemReference();
        ctx = new ToolContext(CONV, "测试");
        tool = new ModifyTodoTool(capability, new ReminderState(), reference, psvc);
        // Create a todo and remember the list
        StudentTodo todo = capability.createTodo(CONV, "测试任务", "明天18:00");
        todoId = todo.id();
        reference.remember(CONV, psvc.listAll(CONV));
    }

    @Test
    @DisplayName("用 TD-xxx 原始 ID 修改截止时间")
    void modifyByRawId() {
        ToolExecution result = tool.execute(
                new ModifyTodoInput(todoId, "后天18:00"), ctx);

        assertThat(result.content()).contains("已改为", "测试任务", "8月7日 18:00");
        StudentTodo modified = capability.findById(todoId, CONV);
        assertThat(modified.dueTime()).isAfter(FIXED.plusSeconds(172800)); // 2 days later
    }

    @Test
    @DisplayName("用自然引用「第1项」修改截止时间")
    void modifyByNaturalRef() {
        ToolExecution result = tool.execute(
                new ModifyTodoInput("第1项", "8月10日09:00"), ctx);

        assertThat(result.content()).contains("已改为", "8月10日 09:00");
    }

    @Test
    @DisplayName("用「刚才的」修改截止时间")
    void modifyByLastOperation() {
        reference.markLastOperation(CONV, todoId);

        ToolExecution result = tool.execute(
                new ModifyTodoInput("刚才的", "大后天22:00"), ctx);

        assertThat(result.content()).contains("已改为");
    }

    @Test
    @DisplayName("拒绝不存在的引用")
    void rejectsInvalidRef() {
        ToolExecution result = tool.execute(
                new ModifyTodoInput("第99项", "明天18:00"), ctx);

        assertThat(result.content()).contains("第99项不存在");
    }

    @Test
    @DisplayName("拒绝过去的时间")
    void rejectsPastTime() {
        // FIXED is 2026-08-05, so "8月1日" is in the past
        assertThatThrownBy(() -> tool.execute(
                new ModifyTodoInput(todoId, "8月1日18:00"), ctx))
                .hasMessageContaining("过去的时间");
    }
}