package com.weida.bot.wechatilinkbot.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.infrastructure.desktop.WindowsDesktopTaskExecutor;
import com.weida.bot.wechatilinkbot.model.DesktopTask;
import com.weida.bot.wechatilinkbot.model.DesktopTaskResult;
import com.weida.bot.wechatilinkbot.model.DesktopTaskStatus;
import com.weida.bot.wechatilinkbot.persistence.DesktopTaskRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DesktopTaskCapabilityTest {

    private static final String CONVERSATION_ID = "wechat-ilink|user-1";
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private DesktopTaskRepository repository;
    private WindowsDesktopTaskExecutor executor;
    private DesktopTaskCapability capability;

    @BeforeEach
    void setUp() {
        DesktopTaskProperties properties = new DesktopTaskProperties();
        properties.setEnabled(true);
        properties.setApprovalExpireAfter(Duration.ofMinutes(5));
        repository = mock(DesktopTaskRepository.class);
        executor = mock(WindowsDesktopTaskExecutor.class);
        when(executor.available()).thenReturn(true);
        capability = new DesktopTaskCapability(
                repository,
                new DesktopTaskPolicy(properties),
                executor,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void isUnavailableWhenTheLocalBrowserGatewayIsUnavailable() {
        when(executor.available()).thenReturn(false);

        assertThat(capability.available()).isFalse();
    }

    @Test
    void createsAnUnexecutedDraftForAnyValidatedHttpsUrl() {
        when(repository.create(any(DesktopTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DesktopTask task = capability.createOpenUrlDraft(
                CONVERSATION_ID,
                "https://new-platform.example/course?id=42#lesson-1");

        assertThat(task.taskId()).startsWith("DT-");
        assertThat(task.status()).isEqualTo(DesktopTaskStatus.WAITING_APPROVAL);
        assertThat(task.url()).isEqualTo(
                "https://new-platform.example/course?id=42#lesson-1");
        verify(repository).create(task);
        verify(executor, never()).openUrl(any());
    }

    @Test
    void rejectsNonHttpsUrlWithoutCreatingATask() {
        assertThatThrownBy(() -> capability.createOpenUrlDraft(
                CONVERSATION_ID,
                "http://example.edu.cn/path"))
                .isInstanceOf(DesktopTaskPolicyViolationException.class)
                .hasMessageContaining("HTTPS");
        verify(repository, never()).create(any());
    }

    @Test
    void acceptsAnotherTrustedConversationWithoutStaticConfiguration() {
        when(repository.create(any(DesktopTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DesktopTask task = capability.createOpenUrlDraft(
                "wechat-ilink|another-user",
                "https://another-platform.example/course");

        assertThat(task.conversationId()).isEqualTo("wechat-ilink|another-user");
        assertThat(task.status()).isEqualTo(DesktopTaskStatus.WAITING_APPROVAL);
    }

    @Test
    void keepsAnExplicitOperatorRestrictionOptionalAndBackwardCompatible() {
        DesktopTaskProperties restrictedProperties = new DesktopTaskProperties();
        restrictedProperties.setEnabled(true);
        restrictedProperties.setAllowedConversationList(List.of(CONVERSATION_ID));
        restrictedProperties.setAllowedUrlHostList(List.of("example.edu.cn"));
        DesktopTaskPolicy restrictedPolicy = new DesktopTaskPolicy(restrictedProperties);

        assertThat(restrictedPolicy.normalizeAndValidateUrl("https://example.edu.cn/course"))
                .hasHost("example.edu.cn");
        assertThatThrownBy(() -> restrictedPolicy.validateConversation("wechat-ilink|other"))
                .isInstanceOf(DesktopTaskPolicyViolationException.class)
                .hasMessageContaining("权限");
        assertThatThrownBy(() ->
                restrictedPolicy.normalizeAndValidateUrl("https://other.example/course"))
                .isInstanceOf(DesktopTaskPolicyViolationException.class)
                .hasMessageContaining("允许列表");
    }

    @Test
    void confirmsTheOwnerDraftAndReturnsTheActualExecutorResult() {
        DesktopTask draft = draft(DesktopTaskStatus.WAITING_APPROVAL, NOW.plus(Duration.ofMinutes(5)));
        when(repository.findById(draft.taskId())).thenReturn(java.util.Optional.of(draft));
        when(repository.update(any(DesktopTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(executor.openUrl(URI.create(draft.url())))
                .thenReturn(DesktopTaskResult.succeeded("已在本机默认浏览器打开 example.edu.cn"));

        DesktopTask task = capability.confirm(draft.taskId(), CONVERSATION_ID);

        assertThat(task.status()).isEqualTo(DesktopTaskStatus.SUCCEEDED);
        assertThat(task.resultCode()).isEqualTo("opened");
        verify(executor).openUrl(URI.create(draft.url()));
    }

    @Test
    void neverLetsAnotherConversationConfirmTheOwnersDraft() {
        DesktopTask draft = draft(DesktopTaskStatus.WAITING_APPROVAL, NOW.plus(Duration.ofMinutes(5)));
        when(repository.findById(draft.taskId())).thenReturn(java.util.Optional.of(draft));

        assertThatThrownBy(() -> capability.confirm(
                draft.taskId(),
                "wechat-ilink|another-user"))
                .isInstanceOf(DesktopTaskPolicyViolationException.class)
                .hasMessageContaining("本人");
        verify(executor, never()).openUrl(any());
    }

    @Test
    void neverExecutesAnExpiredDraft() {
        DesktopTask draft = draft(DesktopTaskStatus.WAITING_APPROVAL, NOW.minusSeconds(1));
        when(repository.findById(draft.taskId())).thenReturn(java.util.Optional.of(draft));
        when(repository.update(any(DesktopTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> capability.confirm(draft.taskId(), CONVERSATION_ID))
                .isInstanceOf(DesktopTaskPolicyViolationException.class)
                .hasMessageContaining("不能确认");
        verify(executor, never()).openUrl(any());
    }

    private static DesktopTask draft(DesktopTaskStatus status, Instant expiry) {
        return new DesktopTask(
                "DT-test-task",
                CONVERSATION_ID,
                "https://example.edu.cn/course",
                status,
                NOW,
                expiry,
                null,
                null,
                null,
                null);
    }
}
