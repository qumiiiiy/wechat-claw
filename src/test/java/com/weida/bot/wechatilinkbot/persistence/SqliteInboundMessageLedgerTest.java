package com.weida.bot.wechatilinkbot.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.model.InboundMessageClaim;
import com.weida.bot.wechatilinkbot.model.InboundMessageIdentity;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InboundMessageLedgerMapper;
import com.weida.bot.wechatilinkbot.persistence.mybatis.InstantTextTypeHandler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** Real SQLite mapping tests for duplicate claims and restart-safe state. */
class SqliteInboundMessageLedgerTest {

    private static final Instant NOW = Instant.parse("2026-08-05T02:00:00Z");
    private DataSource dataSource;
    private SqliteInboundMessageLedger ledger;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = createDataSource("jdbc:sqlite::memory:");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ilink_inbound_message (
                        bot_id TEXT NOT NULL,
                        message_id INTEGER NOT NULL,
                        sender_id TEXT NOT NULL,
                        conversation_id TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 0,
                        first_received_at TEXT NOT NULL,
                        last_received_at TEXT NOT NULL,
                        lease_id TEXT,
                        lease_until TEXT,
                        next_attempt_at TEXT,
                        last_error TEXT,
                        completed_at TEXT,
                        PRIMARY KEY (bot_id, message_id)
                    )
                    """);
        }
        InboundMessageLedgerProperties properties = new InboundMessageLedgerProperties();
        ledger = new SqliteInboundMessageLedger(mapper(), properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    @Test
    void persistsCompletionAndReclaimsAFailedMessageAfterBackoff() {
        InboundMessageIdentity identity = identity(7L);

        InboundMessageClaim first = ledger.claim(
                identity,
                NOW,
                NOW.plusSeconds(30),
                "lease-1");
        assertThat(first.status()).isEqualTo(InboundMessageClaim.Status.CLAIMED);
        assertThat(ledger.claim(
                identity,
                NOW.plusSeconds(1),
                NOW.plusSeconds(31),
                "lease-2").status())
                .isEqualTo(InboundMessageClaim.Status.BUSY);
        assertThat(ledger.complete(identity, "lease-1", NOW.plusSeconds(2))).isTrue();
        assertThat(ledger.claim(
                identity,
                NOW.plusSeconds(3),
                NOW.plusSeconds(33),
                "lease-3").status())
                .isEqualTo(InboundMessageClaim.Status.COMPLETED);

        InboundMessageIdentity failedIdentity = identity(8L);
        ledger.claim(failedIdentity, NOW, NOW.plusSeconds(30), "lease-a");
        Instant retryAt = NOW.plusSeconds(10);
        assertThat(ledger.fail(
                failedIdentity,
                "lease-a",
                retryAt,
                "IllegalStateException",
                false)).isTrue();
        assertThat(ledger.claim(
                failedIdentity,
                retryAt,
                retryAt.plusSeconds(30),
                "lease-b").status())
                .isEqualTo(InboundMessageClaim.Status.CLAIMED);
    }

    private InboundMessageLedgerMapper mapper() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTypeHandlers(new InstantTextTypeHandler());
        factoryBean.setMapperLocations(new ClassPathResource(
                "mapper/InboundMessageLedgerMapper.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        MapperFactoryBean<InboundMessageLedgerMapper> mapperFactory =
                new MapperFactoryBean<>(InboundMessageLedgerMapper.class);
        mapperFactory.setSqlSessionFactory(sqlSessionFactory);
        mapperFactory.afterPropertiesSet();
        return mapperFactory.getObject();
    }

    private static DataSource createDataSource(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        return new HikariDataSource(config);
    }

    private static InboundMessageIdentity identity(long messageId) {
        return new InboundMessageIdentity(
                "bot-a",
                messageId,
                "sender@im.wechat",
                "wechat-ilink|sender@im.wechat",
                NOW);
    }
}
