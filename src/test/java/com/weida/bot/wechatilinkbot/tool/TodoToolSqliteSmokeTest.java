package com.weida.bot.wechatilinkbot.tool;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.ADD_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.CANCEL_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.COMPLETE_TODO;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.LIST_TODOS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.AgentModelClient;
import com.weida.bot.wechatilinkbot.agent.ModelRequest;
import com.weida.bot.wechatilinkbot.agent.ModelResponse;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.DesktopBrowserGateway;
import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.persistence.SqliteStudentTodoMemory;
import com.weida.bot.wechatilinkbot.persistence.StudentTodoMemory;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InstantTextTypeHandler;
import com.weida.bot.wechatilinkbot.persistence.mybatis.StudentTodoMapper;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(properties = {
        "bot.channel.wechat.enabled=false",
        "bot.persistence.conversation.type=sqlite",
        "bot.persistence.conversation.sqlite.jdbc-url=jdbc:sqlite:./target/test-data/todo-e2.db"
})
@Import(TodoToolSqliteSmokeTest.MockInfrastructureConfiguration.class)
@DisplayName("待办 SQLite 持久化烟雾测试")
class TodoToolSqliteSmokeTest {

    private static final String CONV_X = "wechat-ilink|sqlite-user-x";
    private static final String CONV_Y = "wechat-ilink|sqlite-user-y";

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private StudentTodoMemory memory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUpTodos() {
        for (String convId : memory.allConversationIds()) {
            for (StudentTodo todo : memory.listPending(convId)) {
                try { memory.cancel(todo.id(), convId); } catch (Exception ignored) { }
            }
        }
    }

    // ── 0. Spring 注入了正确的实现 ──────────────────────────────────

    @Test
    @DisplayName("Spring 注入的 StudentTodoMemory 是 SqliteStudentTodoMemory")
    void sqliteBackendIsActive() {
        assertThat(memory).isInstanceOf(SqliteStudentTodoMemory.class);
    }

    // ── 1. 重启恢复 ───────────────────────────────────────────────

    @Test
    @DisplayName("重启恢复：创建待办后用新实例读取，数据仍在")
    void dataSurvivesRestart() throws Exception {
        String conv = "wechat-ilink|restart";
        String todoId = addAndGetId(conv, "重启后还在吗", "大后天10:00");

        StudentTodoMemory freshMemory = createFreshMemory();
        List<StudentTodo> pending = freshMemory.listPending(conv);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).id()).isEqualTo(todoId);
        assertThat(pending.get(0).status()).isEqualTo(StudentTodoStatus.PENDING);
    }

    @Test
    @DisplayName("重启恢复：完成状态在重启后保持不变")
    void completedStateSurvivesRestart() throws Exception {
        String conv = "wechat-ilink|restart-complete";
        String todoId = addAndGetId(conv, "要完成的任务", "明天18:00");
        toolRegistry.execute(COMPLETE_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));

        StudentTodoMemory freshMemory = createFreshMemory();
        StudentTodo todo = freshMemory.findById(todoId, conv);
        assertThat(todo).isNotNull();
        assertThat(todo.status()).isEqualTo(StudentTodoStatus.COMPLETED);
        assertThat(freshMemory.listPending(conv)).isEmpty();
    }

    @Test
    @DisplayName("重启恢复：取消状态在重启后保持不变")
    void canceledStateSurvivesRestart() throws Exception {
        String conv = "wechat-ilink|restart-cancel";
        String todoId = addAndGetId(conv, "要取消的任务", "后天15:00");
        toolRegistry.execute(CANCEL_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));

        StudentTodoMemory freshMemory = createFreshMemory();
        StudentTodo todo = freshMemory.findById(todoId, conv);
        assertThat(todo).isNotNull();
        assertThat(todo.status()).isEqualTo(StudentTodoStatus.CANCELED);
        assertThat(freshMemory.listPending(conv)).isEmpty();
    }

    // ── 2. 会话隔离 ────────────────────────────────────────────────

    @Test
    @DisplayName("会话隔离：X 看不到 Y 的待办")
    void sessionIsolationList() {
        addAndGetId(CONV_X, "X的数学作业", "明天10:00");
        addAndGetId(CONV_Y, "Y的英语阅读", "明天15:00");

        String xList = toolRegistry.execute(LIST_TODOS, "{}", ctx(CONV_X)).content();
        assertThat(xList).contains("X的数学作业");
        assertThat(xList).doesNotContain("Y的英语阅读");

        String yList = toolRegistry.execute(LIST_TODOS, "{}", ctx(CONV_Y)).content();
        assertThat(yList).contains("Y的英语阅读");
        assertThat(yList).doesNotContain("X的数学作业");
    }

    @Test
    @DisplayName("会话隔离：X 不能完成 Y 的待办")
    void sessionIsolationComplete() {
        String yTodoId = addAndGetId(CONV_Y, "Y的私密待办", "明天18:00");

        ToolInvocation result = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"" + yTodoId + "\"}", ctx(CONV_X));
        assertThat(result.succeeded()).isFalse();
        assertThat(memory.listPending(CONV_Y))
                .extracting(StudentTodo::title).contains("Y的私密待办");
    }

    @Test
    @DisplayName("会话隔离：X 不能取消 Y 的待办")
    void sessionIsolationCancel() {
        String yTodoId = addAndGetId(CONV_Y, "Y的重要提醒", "后天09:00");

        ToolInvocation result = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"" + yTodoId + "\"}", ctx(CONV_X));
        assertThat(result.succeeded()).isFalse();
        assertThat(memory.listPending(CONV_Y))
                .extracting(StudentTodo::title).contains("Y的重要提醒");
    }

    // ── 3. 状态迁移 ────────────────────────────────────────────────

    @Test
    @DisplayName("状态迁移：PENDING → COMPLETED")
    void stateTransitionComplete() {
        String conv = "wechat-ilink|state-complete";
        String todoId = addAndGetId(conv, "状态迁移测试", "明天20:00");

        ToolInvocation completeResult = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        assertThat(completeResult.succeeded()).isTrue();
        assertThat(completeResult.content()).contains("已标记完成");

        StudentTodo todo = memory.findById(todoId, conv);
        assertThat(todo.status()).isEqualTo(StudentTodoStatus.COMPLETED);
        assertThat(memory.listPending(conv)).isEmpty();
    }

    @Test
    @DisplayName("状态迁移：PENDING → CANCELED")
    void stateTransitionCancel() {
        String conv = "wechat-ilink|state-cancel";
        String todoId = addAndGetId(conv, "要取消的", "后天12:00");

        ToolInvocation cancelResult = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        assertThat(cancelResult.succeeded()).isTrue();
        assertThat(cancelResult.content()).contains("已取消");

        StudentTodo todo = memory.findById(todoId, conv);
        assertThat(todo.status()).isEqualTo(StudentTodoStatus.CANCELED);
        assertThat(memory.listPending(conv)).isEmpty();
    }

    @Test
    @DisplayName("状态迁移：已完成的待办不能再次完成")
    void completedTodoCannotBeCompletedAgain() {
        String conv = "wechat-ilink|state-no-double";
        String todoId = addAndGetId(conv, "防重复测试", "明天16:00");

        toolRegistry.execute(COMPLETE_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        ToolInvocation second = toolRegistry.execute(
                COMPLETE_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        assertThat(second.succeeded()).isFalse();
    }

    @Test
    @DisplayName("状态迁移：已取消的待办不能再次取消")
    void canceledTodoCannotBeCanceledAgain() {
        String conv = "wechat-ilink|state-no-double-cancel";
        String todoId = addAndGetId(conv, "防重复取消", "明天08:00");

        toolRegistry.execute(CANCEL_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        ToolInvocation second = toolRegistry.execute(
                CANCEL_TODO, "{\"todoId\":\"" + todoId + "\"}", ctx(conv));
        assertThat(second.succeeded()).isFalse();
    }

    // ── 4. 稳定排序 ────────────────────────────────────────────────

    @Test
    @DisplayName("稳定排序：listPending 按 dueTime 升序")
    void pendingTodosSortedByDueTimeAscending() {
        String conv = "wechat-ilink|sort";
        addAndGetId(conv, "第三个", "大后天12:00");
        addAndGetId(conv, "第一个", "明天08:00");
        addAndGetId(conv, "第二个", "后天14:00");

        List<StudentTodo> pending = memory.listPending(conv);
        assertThat(pending)
                .extracting(StudentTodo::title)
                .containsExactly("第一个", "第二个", "第三个");
    }

    @Test
    @DisplayName("allConversationIds 返回去重的会话集合")
    void allConversationIdsDistinct() {
        addAndGetId(CONV_X, "A-第一", "明天09:00");
        addAndGetId(CONV_X, "A-第二", "后天10:00");
        addAndGetId(CONV_Y, "Y-第一", "明天09:00");

        Set<String> convIds = memory.allConversationIds();
        assertThat(convIds).contains(CONV_X, CONV_Y);
        assertThat(convIds.stream().filter(c -> c.equals(CONV_X)).count()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────

    private ToolRunContext ctx(String conversationId) {
        return new ToolRunContext(conversationId, "SQLite烟雾测试",
                false, false, 0, objectMapper);
    }

    private String addAndGetId(String conv, String title, String dueTimeStr) {
        toolRegistry.execute(ADD_TODO,
                "{\"title\":\"" + title + "\",\"dueTimeStr\":\"" + dueTimeStr + "\"}",
                ctx(conv));
        return memory.listPending(conv).stream()
                .filter(t -> t.title().equals(title))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到待办: " + title))
                .id();
    }

    private StudentTodoMemory createFreshMemory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeHandlers(new InstantTextTypeHandler());
        factoryBean.setMapperLocations(new org.springframework.core.io.Resource[]{
                new ClassPathResource("mapper/StudentTodoMapper.xml")});
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        var mapperFactory = new MapperFactoryBean<>(StudentTodoMapper.class);
        mapperFactory.setSqlSessionFactory(sqlSessionFactory);
        mapperFactory.afterPropertiesSet();
        StudentTodoMapper mapper = mapperFactory.getObject();

        return new SqliteStudentTodoMemory(mapper);
    }

    // ── mock infrastructure ───────────────────────────────────────

    @TestConfiguration(proxyBeanMethods = false)
    static class MockInfrastructureConfiguration {

        @Bean
        @Primary
        DesktopBrowserGateway availableDesktopBrowserGateway() {
            return new DesktopBrowserGateway() {
                @Override public boolean available() { return true; }
                @Override public void open(URI url) {
                    throw new UnsupportedOperationException("测试不会执行桌面动作");
                }
            };
        }

        @Bean
        @Primary
        AgentModelClient availableModelClient() {
            return new AgentModelClient() {
                @Override public boolean available() { return true; }
                @Override public ModelResponse complete(ModelRequest request) throws IOException {
                    throw new UnsupportedOperationException("SQLite 测试不应调用 LLM");
                }
            };
        }
    }
}