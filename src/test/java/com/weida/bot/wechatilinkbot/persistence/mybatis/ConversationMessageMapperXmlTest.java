package com.weida.bot.wechatilinkbot.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ConversationMessageMapperXmlTest {

    private static final String MAPPER_RESOURCE =
            "mapper/ConversationMessageMapper.xml";
    private static final String NAMESPACE = ConversationMessageMapper.class.getName();

    @Test
    void mapperXmlDefinesEveryDaoStatement() throws IOException {
        Configuration configuration = parseMapper();

        assertThat(configuration.hasStatement(statementId("listRecent")))
                .isTrue();
        assertThat(configuration.hasStatement(statementId("insert")))
                .isTrue();
        assertThat(configuration.hasStatement(statementId("getRetentionCutoffId")))
                .isTrue();
        assertThat(configuration.hasStatement(statementId("deleteOlder")))
                .isTrue();
        assertThat(configuration.hasStatement(statementId("deleteExpired")))
                .isTrue();
        assertThat(configuration.hasStatement(statementId("deleteAll")))
                .isTrue();
    }

    @Test
    void mapperUsesExplicitColumnsAndBoundParameters() throws IOException {
        Configuration configuration = parseMapper();
        MappedStatement statement = configuration.getMappedStatement(
                statementId("listRecent"));

        String sql = statement.getBoundSql(Map.of(
                "conversationId", "bot|user",
                "limit", 10)).getSql();

        assertThat(sql)
                .contains("conversation_id = ?")
                .contains("LIMIT ?")
                .doesNotContain("SELECT *");
    }

    private static Configuration parseMapper() throws IOException {
        ClassPathResource resource = new ClassPathResource(MAPPER_RESOURCE);
        Configuration configuration = new Configuration();
        try (InputStream input = resource.getInputStream()) {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    input,
                    configuration,
                    MAPPER_RESOURCE,
                    configuration.getSqlFragments());
            mapperBuilder.parse();
        }
        return configuration;
    }

    private static String statementId(String methodName) {
        return NAMESPACE + "." + methodName;
    }
}
