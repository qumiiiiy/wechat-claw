package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailProperties;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QQ IMAP 邮件适配器")
class QqImapAdapterTest {

    private QqMailProperties properties;
    private QqImapAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new QqMailProperties();
        adapter = new QqImapAdapter(properties);
    }

    @Test
    @DisplayName("source 返回 EMAIL")
    void sourceReturnsEmail() {
        assertThat(adapter.source()).isEqualTo(PendingItemSource.EMAIL);
    }

    @Test
    @DisplayName("supportsModify 返回 false（只读）")
    void supportsModifyIsFalse() {
        assertThat(adapter.supportsModify()).isFalse();
    }

    @Test
    @DisplayName("邮件功能未启用时返回空列表")
    void disabledReturnsEmpty() {
        properties.setEnabled(false);

        assertThat(adapter.fetchItems("any-conv")).isEmpty();
    }

    @Test
    @DisplayName("无发件地址时返回空列表")
    void noCredentialsReturnsEmptyWhenEnabled() {
        properties.setEnabled(true);
        // senderAddress and authorizationCode are empty by default

        assertThat(adapter.fetchItems("any-conv")).isEmpty();
    }

    @Test
    @DisplayName("adapter 受 ConditionalOnProperty 保护")
    void adapterIsConditional() throws Exception {
        // Verify the class-level annotation exists
        var annotation = QqImapAdapter.class.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("bot.infrastructure.mail.qq");
        assertThat(annotation.name()).contains("enabled");
        assertThat(annotation.havingValue()).isEqualTo("true");
    }
}