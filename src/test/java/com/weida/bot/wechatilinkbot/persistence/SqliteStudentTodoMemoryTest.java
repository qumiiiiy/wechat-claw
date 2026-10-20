package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.weida.bot.wechatilinkbot.model.StudentTodo;
import com.weida.bot.wechatilinkbot.model.StudentTodoStatus;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InstantTextTypeHandler;
import com.weida.bot.wechatilinkbot.persistence.mybatis.StudentTodoMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** Real SQLite integration tests for student todo persistence. */
class SqliteStudentTodoMemoryTest {

    private static final String SESSION_A = "wechat-ilink|userA";
    private static final String SESSION_B = "wechat-ilink|userB";

    private DataSource dataSource;
    private SqliteStudentTodoMemory memory;

    @BeforeEach
    void setUp() throws Exception {
        // In-memory SQLite with shared cache so multiple connections see the same DB
        dataSource = createDataSource("jdbc:sqlite:file::memory:?cache=shared");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE student_todo (
                        id              TEXT PRIMARY KEY,
                        conversation_id TEXT    NOT NULL,
                        title           TEXT    NOT NULL,
                        due_time        TEXT    NOT NULL,
                        status          TEXT    NOT NULL DEFAULT 'PENDING',
                        source          TEXT,
                        created_at      TEXT    NOT NULL
                    )
                    """);
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_student_todo_conv_due
                    ON student_todo (conversation_id, due_time ASC)
                    """);
        }
        memory = new SqliteStudentTodoMemory(mapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS student_todo");
        }
    }

    // ── 1. Restart recovery ────────────────────────────────────────

    @Test
    void dataPersistsAfterRecreatingConnection() throws Exception {
        memory.add(pendingTodo(SESSION_A, "持久化测试",
                Instant.parse("2026-08-01T10:00:00Z")));

        // Simulate restart: recreate memory with a fresh mapper
        var freshMemory = new SqliteStudentTodoMemory(mapper());

        assertThat(freshMemory.listPending(SESSION_A))
                .hasSize(1)
                .first()
                .returns("持久化测试", StudentTodo::title);
    }

    @Test
    void dataDoesNotPersistAcrossSchemaRecreation() throws Exception {
        memory.add(pendingTodo(SESSION_A, "临时数据",
                Instant.parse("2026-08-01T10:00:00Z")));

        // Drop and recreate table (simulating fresh DB)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS student_todo");
            stmt.execute("""
                    CREATE TABLE student_todo (
                        id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL,
                        title TEXT NOT NULL, due_time TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        source TEXT, created_at TEXT NOT NULL
                    )
                    """);
        }
        var freshMemory = new SqliteStudentTodoMemory(mapper());

        assertThat(freshMemory.listPending(SESSION_A)).isEmpty();
    }

    // ── 2. Session isolation ────────────────────────────────────────

    @Test
    void sessionACannotReadSessionBTodos() {
        memory.add(pendingTodo(SESSION_A, "A的", Instant.parse("2026-08-01T10:00:00Z")));
        memory.add(pendingTodo(SESSION_B, "B的", Instant.parse("2026-08-01T10:00:00Z")));

        assertThat(memory.listPending(SESSION_A))
                .extracting(StudentTodo::title).containsExactly("A的");
        assertThat(memory.listPending(SESSION_B))
                .extracting(StudentTodo::title).containsExactly("B的");
    }

    @Test
    void sessionACannotCompleteSessionBTodo() {
        memory.add(pendingTodo(SESSION_A, "A的", Instant.parse("2026-08-01T10:00:00Z")));
        memory.add(pendingTodo(SESSION_B, "B的", Instant.parse("2026-08-01T10:00:00Z")));
        String todoB = memory.listPending(SESSION_B).get(0).id();

        assertThatThrownBy(() -> memory.complete(todoB, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    @Test
    void sessionACannotCancelSessionBTodo() {
        memory.add(pendingTodo(SESSION_B, "B的", Instant.parse("2026-08-01T10:00:00Z")));
        String todoB = memory.listPending(SESSION_B).get(0).id();

        assertThatThrownBy(() -> memory.cancel(todoB, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    // ── 3. Complete and cancel state transitions ────────────────────

    @Test
    void completeChangesStatusAndRemovesFromPending() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();

        memory.complete(id, SESSION_A);

        assertThat(memory.listPending(SESSION_A)).isEmpty();
        assertThat(memory.findById(id, SESSION_A).status())
                .isEqualTo(StudentTodoStatus.COMPLETED);
    }

    @Test
    void cancelChangesStatusAndRemovesFromPending() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();

        memory.cancel(id, SESSION_A);

        assertThat(memory.listPending(SESSION_A)).isEmpty();
        assertThat(memory.findById(id, SESSION_A).status())
                .isEqualTo(StudentTodoStatus.CANCELED);
    }

    @Test
    void completedTodoCannotBeCompletedAgain() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();
        memory.complete(id, SESSION_A);

        assertThatThrownBy(() -> memory.complete(id, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    @Test
    void canceledTodoCannotBeCanceledAgain() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();
        memory.cancel(id, SESSION_A);

        assertThatThrownBy(() -> memory.cancel(id, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    @Test
    void completedTodoCannotBeCanceled() {
        memory.add(pendingTodo(SESSION_A, "任务", Instant.parse("2026-08-01T10:00:00Z")));
        String id = memory.listPending(SESSION_A).get(0).id();
        memory.complete(id, SESSION_A);

        assertThatThrownBy(() -> memory.cancel(id, SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    // ── 4. UPDATE session condition ─────────────────────────────────

    @Test
    void updateOnlyAffectsMatchingSession() {
        memory.add(pendingTodo(SESSION_A, "A的", Instant.parse("2026-08-01T10:00:00Z")));
        memory.add(pendingTodo(SESSION_B, "B的", Instant.parse("2026-08-01T10:00:00Z")));
        String todoA = memory.listPending(SESSION_A).get(0).id();

        memory.complete(todoA, SESSION_A);

        // B's todo must still be pending
        assertThat(memory.listPending(SESSION_B)).hasSize(1);
    }

    @Test
    void nonExistentIdReturnsZeroRowsUpdated() {
        assertThatThrownBy(() -> memory.complete("TD-not-exist", SESSION_A))
                .isInstanceOf(StudentTodoPersistenceException.class);
    }

    // ── 5. Time queries ─────────────────────────────────────────────

    @Test
    void listPendingOrdersByDueTimeAscending() {
        memory.add(pendingTodo(SESSION_A, "第三", Instant.parse("2026-08-05T10:00:00Z")));
        memory.add(pendingTodo(SESSION_A, "第一", Instant.parse("2026-07-31T09:00:00Z")));
        memory.add(pendingTodo(SESSION_A, "第二", Instant.parse("2026-08-01T14:00:00Z")));

        assertThat(memory.listPending(SESSION_A))
                .extracting(StudentTodo::title)
                .containsExactly("第一", "第二", "第三");
    }

    @Test
    void allConversationIdsReturnsDistinctSessions() {
        memory.add(pendingTodo(SESSION_A, "A-1", Instant.parse("2026-08-01T10:00:00Z")));
        memory.add(pendingTodo(SESSION_A, "A-2", Instant.parse("2026-08-02T10:00:00Z")));
        memory.add(pendingTodo(SESSION_B, "B-1", Instant.parse("2026-08-01T10:00:00Z")));

        Set<String> ids = memory.allConversationIds();
        assertThat(ids).containsExactlyInAnyOrder(SESSION_A, SESSION_B);
    }

    @Test
    void deleteCompletedOlderThanRemovesOldCompletedTodos() {
        memory.add(pendingTodo(SESSION_A, "老任务", Instant.parse("2026-07-01T10:00:00Z")));
        memory.add(pendingTodo(SESSION_A, "新任务", Instant.parse("2026-08-01T10:00:00Z")));
        String oldId = memory.listPending(SESSION_A).stream()
                .filter(t -> t.title().equals("老任务")).findFirst().orElseThrow().id();
        memory.complete(oldId, SESSION_A);

        // Use a future cutoff since todos are created with Instant.now()
        int deleted = memory.deleteCompletedOlderThan(
                Instant.now().plusSeconds(3600));

        assertThat(deleted).isEqualTo(1);
        assertThat(memory.findById(oldId, SESSION_A)).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────

    private StudentTodoMapper mapper() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeHandlers(new InstantTextTypeHandler());
        factoryBean.setMapperLocations(new org.springframework.core.io.Resource[]{
                new ClassPathResource("mapper/StudentTodoMapper.xml")});
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        var mapperFactory = new MapperFactoryBean<>(StudentTodoMapper.class);
        mapperFactory.setSqlSessionFactory(sqlSessionFactory);
        mapperFactory.afterPropertiesSet();
        return mapperFactory.getObject();
    }

    private static DataSource createDataSource(String jdbcUrl) {
        var config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        return new com.zaxxer.hikari.HikariDataSource(config);
    }

    private static StudentTodo pendingTodo(
            String conversationId, String title, Instant dueTime) {
        return new StudentTodo(
                "TD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                conversationId, title, dueTime,
                StudentTodoStatus.PENDING, "manual", Instant.now());
    }
}
