package com.weida.bot.wechatilinkbot.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QqMailPropertiesTest {

    @Test
    void reportsEachMissingEnabledConfigurationByEnvironmentVariableName() {
        QqMailProperties properties = new QqMailProperties();
        properties.setEnabled(true);
        properties.setSenderAddress("sender@qq.com");
        properties.setAuthorizationCode("authorization-code");

        Set<String> messageSet = validationMessages(properties);

        assertThat(messageSet).containsExactlyInAnyOrder(
                "邮件工具启用时必须配置 BOT_EMAIL_ALLOWED_CONVERSATIONS",
                "邮件工具启用时必须配置 BOT_EMAIL_ALLOWED_RECIPIENTS");
    }

    @Test
    void acceptsCompleteEnabledConfiguration() {
        QqMailProperties properties = new QqMailProperties();
        properties.setEnabled(true);
        properties.setSenderAddress("sender@qq.com");
        properties.setAuthorizationCode("authorization-code");
        properties.setAllowedConversationList(List.of("*"));
        properties.setAllowedRecipientList(List.of("friend@example.com"));

        assertThat(validationMessages(properties)).isEmpty();
    }

    @Test
    void boundsTheDraftApprovalLifetime() {
        QqMailProperties properties = new QqMailProperties();

        assertThat(properties.getApprovalExpireAfter()).isEqualTo(Duration.ofMinutes(30));

        properties.setApprovalExpireAfter(Duration.ofHours(1).plusSeconds(1));

        assertThat(validationMessages(properties)).contains(
                "邮件工具的审批有效期必须在 1 秒到 1 小时之间，SMTP 超时必须大于 0");
    }

    private static Set<String> validationMessages(QqMailProperties properties) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties).stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
