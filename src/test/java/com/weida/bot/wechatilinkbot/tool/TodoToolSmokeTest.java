package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.ADD_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CANCEL_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.COMPLETE_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.DAILY_SUMMARY;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LIST_TODOS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.QUERY_TODOS;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.TODAY_TODOS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.AgentModelClient;
import com.weida.bot.wechatilinkbot.agent.AgentRuntime;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.DesktopBrowserGateway;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "bot.channel.wechat.enabled=false",
        "bot.persistence.conversation.type=memory"
})
@Import(TodoToolSmokeTest.MockDesktopConfiguration.class)
@DisplayName("待办工具 Spring 集成烟雾测试")
class TodoToolSmokeTest {

    private static final String CONV_A = "wechat-ilink|smoke-user-a";
    private static final String CONV_B = "wechat-ilink|smoke-user-b";

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentRuntime agentRuntime;

    @Autowired
    private StudentTodoMemory memory;

    @Autowired
    private ObjectMapper objectMapper;

    private ToolRunContext ctx(String conversationId) {
        return new ToolRunContext(conversationId, "烟雾测试", false, false, 0, objectMapper);
    }

    // ── 1. 全部 7 个待办工具已注册 ──────────────────────────────────

    @Test
    @DisplayName("全部 7 个待办工具已在 Spring 容器中注册且可用")
    void allSevenTodoToolsRegisteredAndAvailable() {
        assertThat(toolRegistry.available(ADD_TODO)).isTrue();
        assertThat(toolRegistry.available(LIST_TODOS)).isTrue();
        assertThat(toolRegistry.available(QUERY_TODOS)).isTrue();
        assertThat(toolRegistry.available(TODAY_TODOS)).isTrue();
        assertThat(toolRegistry.available(COMPLETE_TODO)).isTrue();
        assertThat(toolRegistry.available(CANCEL_TODO)).isTrue();
        assertThat(toolRegistry.available(DAILY_SUMMARY)).isTrue();

        assertThat(toolRegistry.listDefinitions(true, Set.of()))
                .extracting(ToolDefinition::name)
                .doesNotContain(
                        ADD_TODO, LIST_TODOS, QUERY_TODOS,
                        TODAY_TODOS, COMPLETE_TODO, CANCEL_TODO,
                        DAILY_SUMMARY);

        assertThat(toolRegistry.listDefinitions(true, Set.of(
                ADD_TODO, LIST_TODOS, QUERY_TODOS,
                TODAY_TODOS, COMPLETE_TODO, CANCEL_TODO,
                DAILY_SUMMARY)))
                .extracting(ToolDefinition::name)
                .contains(
                        ADD_TODO, LIST_TODOS,
                        QUERY_TODOS, TODAY_TODOS,
                        COMPLETE_TODO, CANCEL_TODO,
                        DAILY_SUMMARY);
    }

    // ── 2. 新增待办 ────────────────────────────────────────────────

    @Test
    @DisplayName("add_todo 创建待办（不含 TD-xxx）")
    void addTodoCreatesTodoWithId() {
        ToolInvocation result = toolRegistry.execute(
                ADD_TODO,
                "{\"title\":\"交数学作业\",\"dueTimeStr\":\"明天18:00\"}",
                ctx(CONV_A));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.content()).contains("已创建待办", "交数学作业");
        assertThat(result.content()).doesNotContain("TD-");
    }

    @Test
    @DisplayName("add_todo 拒绝空标题")
    void addTodoRejectsEmptyTitle() {
        ToolInvocation result = toolRegistry.execute(
                ADD_TODO,
                "{\"title\":\"\",\"dueTimeStr\":\"明天18:00\"}",
                ctx(CONV_A));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.content()).contains("标题");
    }

    // ── 3. 列出全部待办 ──────────────────────────────────────────

    @Test
    @DisplayName("list_todos 列出当前会话全部待办")
    void listTodosReturnsCreatedTodos() {
        String conv = "wechat-ilink|smoke-list";
        String id = addAndGetId(conv, "英语阅读", "明天20:00");

        String content = toolRegistry.execute(LIST_TODOS, "{}", ctx(conv)).content();
        assertThat(content).contains("英语阅读");
        assertThat(content).doesNotContain("TD-");
    }

    @Test
    @DisplayName("list_todos 空列表给出友好提示")
    void listTodosEmptyShowsFriendlyMessage() {
        String content = toolRegistry.execute(
                LIST_TODOS, "{}",
                ctx("wechat-ilink|smoke-empty-list")).content();
        assertThat(content).contains("当前没有待办事项");
    }

    // ── 4. 按维度查询待办 ────────────────────────────────────────

    @Test
    @DisplayName("query_todos 按 ALL/TODAY/TOMORROW/WEEK/OVERDUE 维度查询")
    void queryTodosByAllDimensions() {
        String conv = "wechat-ilink|smoke-query";
        addAndGetId(conv, "明天考试复习", "明天09:00");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{\"filter\":\"ALL\"}", ctx(conv)).content())
                .contains("明天考试复习");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{\"filter\":\"TOMORROW\"}", ctx(conv)).content())
                .contains("明天考试复习");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{\"filter\":\"TODAY\"}", ctx(conv)).content())
                .isNotEmpty().doesNotContain("status\":\"error");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{\"filter\":\"WEEK\"}", ctx(conv)).content())
                .isNotEmpty().doesNotContain("status\":\"error");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{\"filter\":\"OVERDUE\"}", ctx(conv)).content())
                .isNotEmpty().doesNotContain("status\":\"error");
    }

    @Test
    @DisplayName("query_todos 未传 filter 时默认 ALL")
    void queryTodosDefaultsToAll() {
        String conv = "wechat-ilink|smoke-default";
        addAndGetId(conv, "默认查询测试", "后天15:00");

        assertThat(toolRegistry.execute(QUERY_TODOS, "{}", ctx(conv)).content())
                .contains("默认查询测试");
    }

    // ── 5. 今日待办 ──────────────────────────────────────────────

    @Test
    @DisplayName("today_todos 返回今天截止的待办")
    void todayTodosReturnsNonError() {
        String conv = "wechat-ilink|smoke-today";
        addAndGetId(conv, "今晚提交报告", "今天23:30");

        String content = toolRegistry.execute(TODAY_TODOS, "{}", ctx(conv)).content();
        assertThat(content).isNotEmpty();
        assertThat(content).contains("今晚提交报告");
    }

    // ── 6. 今日要务 ──────────────────────────────────────────────

    @Test
    @DisplayName("daily_summary 返回今日要务摘要")
    void dailySummaryReturnsFormattedOutput() {
        String conv = "wechat-ilink|smoke-summary";
        addAndGetId(conv, "周报", "明天22:00");
        addAndGetId(conv, "预习第三章", "明天10:00");

        String content = toolRegistry.execute(DAILY_SUMMARY, "{}", ctx(conv)).content();
        assertThat(content).contains("今日要务");
        assertThat(content).contains("周报");
        assertThat(content).contains("预习第三章");
    }

    @Test
    @DisplayName("daily_summary 无待办时给出空状态")
    void dailySummaryEmpty() {
        String content = toolRegistry.execute(
                DAILY_SUMMARY, "{}",
                ctx("wechat-ilink|smoke-summary-empty")).content();
        assertThat(content).contains("当前没有待办事项");
    }

    // ── 7. 完成待办 ──────────────────────────────────────────────

    @Test
    @DisplayName("complete_todo 标记完成并从待办列表移除")
    void completeTodoRemovesFromPending() {
        String conv = "wechat-ilink|smoke-complete";
        String todoId = addAndGetId(conv, "要完成的作业", "明天18:00");

        String completeContent = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv)).content();
        assertThat(completeContent).contains("已标记完成");

        String listContent = toolRegistry.execute(LIST_TODOS, "{}", ctx(conv)).content();
        assertThat(listContent).doesNotContain("要完成的作业");
    }

    @Test
    @DisplayName("complete_todo 拒绝无法理解的引用")
    void completeTodoRejectsNonExistentId() {
        ToolInvocation result = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"TD-not-exist\"}", ctx(CONV_A));
        assertThat(result.content()).contains("无法理解");
    }

    // ── 8. 取消待办 ──────────────────────────────────────────────

    @Test
    @DisplayName("cancel_todo 取消待办并从列表移除")
    void cancelTodoRemovesFromPending() {
        String conv = "wechat-ilink|smoke-cancel";
        String todoId = addAndGetId(conv, "要取消的任务", "后天12:00");

        String cancelContent = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv)).content();
        assertThat(cancelContent).contains("已取消");

        String listContent = toolRegistry.execute(LIST_TODOS, "{}", ctx(conv)).content();
        assertThat(listContent).doesNotContain("要取消的任务");
    }

    @Test
    @DisplayName("cancel_todo 拒绝无法理解的引用")
    void cancelTodoRejectsNonExistentId() {
        ToolInvocation result = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"TD-fake-deadbeef\"}", ctx(CONV_A));
        assertThat(result.content()).contains("无法理解");
    }

    // ── 9. 会话隔离 ──────────────────────────────────────────────

    @Test
    @DisplayName("会话 A 看不到会话 B 的待办")
    void sessionIsolationBetweenConversations() {
        addAndGetId(CONV_A, "A的私事", "明天10:00");
        addAndGetId(CONV_B, "B的私事", "明天15:00");

        String aList = toolRegistry.execute(LIST_TODOS, "{}", ctx(CONV_A)).content();
        assertThat(aList).contains("A的私事");
        assertThat(aList).doesNotContain("B的私事");

        String bList = toolRegistry.execute(LIST_TODOS, "{}", ctx(CONV_B)).content();
        assertThat(bList).contains("B的私事");
        assertThat(bList).doesNotContain("A的私事");
    }

    @Test
    @DisplayName("会话 A 不能完成会话 B 的待办")
    void sessionACannotCompleteSessionBTodo() {
        String bTodoId = addAndGetId(CONV_B, "B的作业", "明天18:00");

        ToolInvocation result = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"" + bTodoId + "\"}", ctx(CONV_A));

        assertThat(result.succeeded()).isFalse();
    }

    @Test
    @DisplayName("会话 A 不能取消会话 B 的待办")
    void sessionACannotCancelSessionBTodo() {
        String bTodoId = addAndGetId(CONV_B, "B的项目", "后天09:00");

        ToolInvocation result = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"" + bTodoId + "\"}", ctx(CONV_A));

        assertThat(result.succeeded()).isFalse();
    }

    // ── E-3：AgentRuntime 直调路径 ───────────────────────────────

    @Test
    @DisplayName("AgentRuntime: 发送「查待办」→ 直调 list_todos")
    void agentRuntimeDispatchesListTodos() {
        String conv = "wechat-ilink|ar-list";
        addAndGetId(conv, "AR测试任务", "明天22:00");

        AgentReply reply = agentRuntime.reply(
                new AgentRequest(conv, "查待办", List.of())).orElseThrow();
        assertThat(reply.text()).contains("AR测试任务");
    }

    @Test
    @DisplayName("AgentRuntime: 发送「今天有什么」→ 直调 today_todos")
    void agentRuntimeDispatchesTodayTodos() {
        String conv = "wechat-ilink|ar-today";
        addAndGetId(conv, "今晚交报告", "今天23:59");

        AgentReply reply = agentRuntime.reply(
                new AgentRequest(conv, "今天有什么", List.of())).orElseThrow();
        assertThat(reply.text()).contains("今晚交报告");
    }

    @Test
    @DisplayName("AgentRuntime: 发送「今日要务」→ 直调 daily_summary")
    void agentRuntimeDispatchesDailySummary() {
        String conv = "wechat-ilink|ar-summary";
        addAndGetId(conv, "写周报", "明天21:00");

        AgentReply reply = agentRuntime.reply(
                new AgentRequest(conv, "今日要务", List.of())).orElseThrow();
        assertThat(reply.text()).contains("今日要务");
        assertThat(reply.text()).contains("写周报");
    }

    @Test
    @DisplayName("AgentRuntime: 空列表时「查待办」返回友好提示")
    void agentRuntimeListTodosEmpty() {
        AgentReply reply = agentRuntime.reply(
                new AgentRequest("wechat-ilink|ar-empty", "查待办", List.of())).orElseThrow();
        assertThat(reply.text()).contains("当前没有待办事项");
    }

    @Test
    @DisplayName("AgentRuntime: 一条消息包含多个待办操作时拒绝")
    void agentRuntimeRejectsMultipleTodoCommands() {
        AgentReply reply = agentRuntime.reply(
                new AgentRequest(CONV_A, "提醒我交作业 明天18:00\n查待办", List.of())).orElseThrow();
        assertThat(reply.text()).contains("多个待办操作", "分别发送");
    }

    // ── helpers ──────────────────────────────────────────────────

    /** Creates a todo and returns its internal ID from the persistence layer. */
    private String addAndGetId(String conv, String title, String dueTimeStr) {
        toolRegistry.execute(ADD_TODO,
                "{\"title\":\"" + title + "\",\"dueTimeStr\":\"" + dueTimeStr + "\"}",
                ctx(conv));
        // ID is not in the response anymore; retrieve from the persistence layer
        List<StudentTodo> pending = memory.listPending(conv);
        return pending.stream()
                .filter(t -> t.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到待办: " + title))
                .id();
    }

    // ── mock infrastructure ───────────────────────────────────────

    @TestConfiguration(proxyBeanMethods = false)
    static class MockDesktopConfiguration {

        @Bean
        @Primary
        DesktopBrowserGateway availableDesktopBrowserGateway() {
            return new DesktopBrowserGateway() {
                @Override
                public boolean available() { return true; }
                @Override
                public void open(URI url) {
                    throw new UnsupportedOperationException("测试不会执行桌面动作");
                }
            };
        }

        @Bean
        @Primary
        AgentModelClient availableModelClient() {
            return new AgentModelClient() {
                @Override
                public boolean available() { return true; }
                @Override
                public ModelResponse complete(ModelRequest request) throws IOException {
                    throw new UnsupportedOperationException("直调路径不应调用 LLM");
                }
            };
        }
    }
}
