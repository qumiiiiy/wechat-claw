package com.weida.bot.wechatilinkbot.persistence.mybatis;

import com.weida.bot.wechatilinkbot.persistence.ConversationMemoryProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.sqlite.SQLiteConfig;

/** SQLite DataSource, schema initialization, and MyBatis wiring for local persistence. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "bot.persistence.conversation",
        name = "type",
        havingValue = "sqlite")
@MapperScan(
        basePackageClasses = ConversationMessageMapper.class,
        sqlSessionFactoryRef = "conversationSqlSessionFactory")
public class SqliteConversationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteConversationConfiguration.class);
    private static final String MAPPER_LOCATION = "classpath*:mapper/*.xml";
    private static final String SQLITE_JDBC_PREFIX = "jdbc:sqlite:";

    @Bean(destroyMethod = "close")
    HikariDataSource conversationMemoryDataSource(ConversationMemoryProperties properties) {
        ConversationMemoryProperties.Sqlite sqlite = properties.getSqlite();
        String jdbcUrl = sqlite.getJdbcUrl().strip();
        createDatabaseParentDirectory(jdbcUrl);
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setBusyTimeout(sqlite.getBusyTimeoutMillis());

        HikariConfig config = new HikariConfig();
        config.setPoolName("conversation-memory");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl(jdbcUrl);
        config.setDataSourceProperties(sqliteConfig.toProperties());
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(sqlite.getConnectionTimeout().toMillis());
        config.setValidationTimeout(sqlite.getValidationTimeout().toMillis());
        config.setAutoCommit(true);
        HikariDataSource dataSource = new HikariDataSource(config);
        initializeSchema(dataSource);
        LOG.info("sqlite_conversation_memory_initialized jdbcUrl={}", redactJdbcUrl(jdbcUrl));
        return dataSource;
    }

    @Bean
    SqlSessionFactory conversationSqlSessionFactory(DataSource conversationMemoryDataSource)
            throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(conversationMemoryDataSource);
        factoryBean.setTypeHandlers(new InstantTextTypeHandler());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(resolver.getResources(MAPPER_LOCATION));
        return Objects.requireNonNull(factoryBean.getObject(), "无法创建会话 MyBatis SqlSessionFactory");
    }

    @Bean
    PlatformTransactionManager conversationTransactionManager(DataSource conversationMemoryDataSource) {
        return new DataSourceTransactionManager(conversationMemoryDataSource);
    }

    private static void initializeSchema(DataSource dataSource) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema/sqlite-schema.sql"));
        populator.execute(dataSource);
    }

    private static void createDatabaseParentDirectory(String jdbcUrl) {
        String databaseLocation = jdbcUrl.substring(SQLITE_JDBC_PREFIX.length());
        if (databaseLocation.startsWith(":memory:") || databaseLocation.startsWith("file:")) {
            return;
        }
        try {
            Path databaseFile = Path.of(databaseLocation).toAbsolutePath().normalize();
            Path parentDirectory = databaseFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 SQLite 数据库目录", exception);
        }
    }

    private static String redactJdbcUrl(String jdbcUrl) {
        return jdbcUrl.startsWith(SQLITE_JDBC_PREFIX) ? SQLITE_JDBC_PREFIX + "configured" : "invalid";
    }
}
