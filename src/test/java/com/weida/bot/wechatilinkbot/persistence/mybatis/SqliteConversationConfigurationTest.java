package com.weida.bot.wechatilinkbot.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.model.DesktopTaskStatus;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemoryProperties;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteConversationConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsTheDatabaseSchemaAndMapsInstantColumns() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("nested").resolve("conversation.db");
        ConversationMemoryProperties properties = new ConversationMemoryProperties();
        assertThat(properties.getType()).isEqualTo("sqlite");
        properties.getSqlite().setJdbcUrl("jdbc:sqlite:" + databaseFile);
        SqliteConversationConfiguration configuration = new SqliteConversationConfiguration();

        try (HikariDataSource dataSource = configuration.conversationMemoryDataSource(properties)) {
            SqlSessionFactory factory = configuration.conversationSqlSessionFactory(dataSource);
            try (SqlSession session = factory.openSession(true)) {
                ConversationMessageMapper mapper = session.getMapper(ConversationMessageMapper.class);
                mapper.insert("bot|user", "user", "你好");

                List<ConversationMessage> messageList = mapper.listRecent("bot|user", 10);

                assertThat(messageList).singleElement().satisfies(message -> {
                    assertThat(message.id()).isPositive();
                    assertThat(message.content()).isEqualTo("你好");
                    assertThat(message.createdAt()).isNotNull();
                    assertThat(message.updatedAt()).isNotNull();
                });

                mapper.upsertSummary("bot|user", "已确认使用中文。", Instant.now());
                assertThat(mapper.findSummary("bot|user")).isEqualTo("已确认使用中文。");

                DesktopTaskMapper desktopTaskMapper = session.getMapper(DesktopTaskMapper.class);
                DesktopTask desktopTask = new DesktopTask(
                        "DT-schema-test",
                        "bot|user",
                        "https://example.edu.cn/course",
                        DesktopTaskStatus.WAITING_APPROVAL,
                        Instant.now(),
                        Instant.now().plusSeconds(300),
                        null,
                        null,
                        null,
                        null);
                desktopTaskMapper.insert(desktopTask);

                assertThat(desktopTaskMapper.findById("DT-schema-test"))
                        .isEqualTo(desktopTask);
            }
        }

        assertThat(databaseFile).exists();
    }
}
