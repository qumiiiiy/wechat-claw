package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailClient;
import com.weida.bot.wechatilinkbot.infrastructure.mail.QqMailProperties;
import com.weida.bot.wechatilinkbot.model.EmailMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailCapabilityTest {

    private static final String CONVERSATION_ID = "wechat-ilink|user-1";
    private static final Instant NOW = Instant.parse("2026-07-23T03:00:00Z");

    private QqMailProperties properties;
    private QqMailClient mailClient;
    private EmailMessage request;

    @BeforeEach
    void setUp() {
        properties = configuredProperties();
        mailClient = mock(QqMailClient.class);
        request = new EmailMessage(
                List.of("friend@example.com"),
                "会议安排",
                "我们明天下午三点开会。");
    }

    @Test
    void preparesDraftWithoutSendingAndSendsOnlyAfterSeparateConfirmation() {
        EmailCapability service = serviceAt(NOW);

        EmailMessage prepared = service.prepareEmail(
                CONVERSATION_ID,
                "请帮我发邮件给朋友",
                request);

        assertThat(prepared).isEqualTo(request);
        verify(mailClient, never()).send(request);

        EmailMessage sent = service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送");

        assertThat(sent).isEqualTo(request);
        verify(mailClient).send(request);
        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有待确认");
    }

    @Test
    void rejectsPrepareWithoutExplicitSendIntent() {
        EmailCapability service = serviceAt(NOW);

        assertThatThrownBy(() -> service.prepareEmail(
                CONVERSATION_ID,
                "帮我写一封邮件草稿",
                request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未明确要求发送");
        verify(mailClient, never()).send(request);
    }

    @Test
    void acceptsPrepareAfterRuntimeVerifiesAMultiTurnSendIntent() {
        EmailCapability service = serviceAt(NOW);

        EmailMessage prepared = service.prepareEmail(
                CONVERSATION_ID,
                "就是杭州的天气，请写得温柔一些",
                true,
                request);

        assertThat(prepared).isEqualTo(request);
        verify(mailClient, never()).send(request);
    }

    @Test
    void replacesAnUnconfirmedDraftWhenTheUserRevisesItsContent() {
        EmailCapability service = serviceAt(NOW);
        EmailMessage revisedRequest = new EmailMessage(
                request.recipientList(),
                request.subject(),
                "我们明天下午四点开会,请提前十分钟到达。");
        service.prepareEmail(CONVERSATION_ID, "请发邮件", request);

        EmailMessage prepared = service.prepareEmail(
                CONVERSATION_ID,
                "把这个邮件的正文改成明天下午四点开会",
                true,
                revisedRequest);

        assertThat(prepared).isEqualTo(revisedRequest);
        service.confirmAndSendEmail(CONVERSATION_ID, "确认发送");

        verify(mailClient, never()).send(request);
        verify(mailClient).send(revisedRequest);
    }

    @Test
    void keepsTheLatestDraftAvailableDuringALongRevisionSession() {
        properties.setApprovalExpireAfter(Duration.ofMinutes(30));
        MutableClock clock = new MutableClock(NOW);
        EmailCapability service = new EmailCapability(properties, mailClient, clock);
        EmailMessage revisedRequest = new EmailMessage(
                request.recipientList(),
                request.subject(),
                "我们明天下午四点开会,请提前十分钟到达。");
        service.prepareEmail(CONVERSATION_ID, "请发邮件", request);
        clock.advance(Duration.ofMinutes(10));
        service.prepareEmail(
                CONVERSATION_ID,
                "把这个邮件的正文改成明天下午四点开会",
                true,
                revisedRequest);
        clock.advance(Duration.ofMinutes(8));

        assertThat(service.confirmAndSendEmail(CONVERSATION_ID, "确认发送"))
                .isEqualTo(revisedRequest);
        verify(mailClient).send(revisedRequest);
    }

    @Test
    void rejectsConversationOutsideIdentityAllowlist() {
        properties.setAllowedConversationList(List.of("wechat-ilink|owner"));
        EmailCapability service = serviceAt(NOW);

        assertThatThrownBy(() -> service.prepareEmail(
                CONVERSATION_ID,
                "请帮我发邮件",
                request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前会话未获授权");
        verify(mailClient, never()).send(request);
    }

    @Test
    void acceptsMessageRecipientWhenAnyRecipientIsExplicitlyAllowed() {
        properties.setAllowedConversationList(List.of(CONVERSATION_ID));
        properties.setAllowedRecipientList(List.of("*"));
        EmailCapability service = serviceAt(NOW);
        EmailMessage dynamicRecipientRequest = new EmailMessage(
                List.of("dynamic-recipient@example.org"),
                "杭州今日天气",
                "杭州今天晴。");

        EmailMessage prepared = service.prepareEmail(
                CONVERSATION_ID,
                "发送杭州天气到 dynamic-recipient@example.org",
                true,
                dynamicRecipientRequest);

        assertThat(prepared).isEqualTo(dynamicRecipientRequest);
        verify(mailClient, never()).send(dynamicRecipientRequest);

        service.confirmAndSendEmail(CONVERSATION_ID, "确认发送");

        verify(mailClient).send(dynamicRecipientRequest);
    }

    @Test
    void rejectsNonAllowlistedRecipientAndHeaderInjection() {
        EmailCapability service = serviceAt(NOW);
        EmailMessage untrustedRecipient = new EmailMessage(
                List.of("stranger@example.net"),
                "主题",
                "正文");
        EmailMessage injectedSubject = new EmailMessage(
                List.of("friend@example.com"),
                "主题\r\nBcc: hidden@example.net",
                "正文");

        assertThatThrownBy(() -> service.prepareEmail(
                CONVERSATION_ID,
                "发送邮件",
                untrustedRecipient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlist");
        assertThatThrownBy(() -> service.prepareEmail(
                CONVERSATION_ID,
                "发送邮件",
                injectedSubject))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("包含换行");
    }

    @Test
    void requiresExactConfirmationAndExpiresPendingDraft() {
        MutableClock clock = new MutableClock(NOW);
        EmailCapability service = new EmailCapability(properties, mailClient, clock);
        service.prepareEmail(CONVERSATION_ID, "请发送这封邮件", request);

        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "好的，确认发送吧"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单独回复");

        clock.advance(properties.getApprovalExpireAfter());

        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已过期");
        verify(mailClient, never()).send(request);
    }

    @Test
    void clearRemovesPendingDraft() {
        EmailCapability service = serviceAt(NOW);
        service.prepareEmail(CONVERSATION_ID, "请发邮件", request);

        service.clearPendingEmail(CONVERSATION_ID);

        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有待确认");
    }

    @Test
    void consumesApprovalAfterAmbiguousSmtpFailure() {
        EmailCapability service = serviceAt(NOW);
        service.prepareEmail(CONVERSATION_ID, "请发邮件", request);
        doThrow(new IllegalStateException("connection closed after DATA"))
                .when(mailClient).send(request);

        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有待确认");
    }

    @Test
    void reportsSafeAvailabilityReasonWithoutExposingConfigurationValues() {
        properties.setEnabled(false);
        EmailCapability disabledService = serviceAt(NOW);

        assertThat(disabledService.available()).isFalse();
        assertThat(disabledService.availabilityReason()).isEqualTo("disabled");

        properties.setEnabled(true);
        properties.setSenderAddress("");
        EmailCapability incompleteService = serviceAt(NOW);

        assertThat(incompleteService.available()).isFalse();
        assertThat(incompleteService.availabilityReason())
                .isEqualTo("sender_address_missing");
    }

    @Test
    void enforcesPerConversationHourlyAttemptLimit() {
        properties.setMaxEmailsPerConversationPerHour(1);
        MutableClock clock = new MutableClock(NOW);
        EmailCapability service = new EmailCapability(properties, mailClient, clock);
        service.prepareEmail(CONVERSATION_ID, "请发邮件", request);
        service.confirmAndSendEmail(CONVERSATION_ID, "确认发送");
        service.prepareEmail(CONVERSATION_ID, "再发一封邮件", request);

        assertThatThrownBy(() -> service.confirmAndSendEmail(
                CONVERSATION_ID,
                "确认发送"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前会话邮件发送频率");

        clock.advance(Duration.ofHours(1));
        service.prepareEmail(CONVERSATION_ID, "一小时后再发邮件", request);

        assertThat(service.confirmAndSendEmail(CONVERSATION_ID, "确认发送"))
                .isEqualTo(request);
    }

    private EmailCapability serviceAt(Instant instant) {
        return new EmailCapability(
                properties,
                mailClient,
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static QqMailProperties configuredProperties() {
        QqMailProperties properties = new QqMailProperties();
        properties.setEnabled(true);
        properties.setSenderAddress("sender@qq.com");
        properties.setAuthorizationCode("authorization-code");
        properties.setAllowedConversationList(List.of("*"));
        properties.setAllowedRecipientList(List.of("friend@example.com"));
        properties.setApprovalExpireAfter(Duration.ofMinutes(5));
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
