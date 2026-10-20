package com.weida.bot.wechatilinkbot.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.capability.EmailCapability;
import com.weida.bot.wechatilinkbot.model.EmailMessage;
import com.weida.bot.wechatilinkbot.model.GeneratedMedia;
import com.weida.bot.wechatilinkbot.model.GeneratedMediaKind;
import com.weida.bot.wechatilinkbot.tool.input.EmailToolInput;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailToolTest {

    private static final ToolContext TOOL_CONTEXT = new ToolContext(
            "wechat-ilink|user-1",
            "请发邮件给 friend@example.com",
            true,
            false,
            false);

    private EmailCapability emailCapability;
    private ObjectMapper objectMapper;
    private EmailTool tool;

    @BeforeEach
    void setUp() {
        emailCapability = mock(EmailCapability.class);
        when(emailCapability.available()).thenReturn(true);
        when(emailCapability.approvalExpireSeconds()).thenReturn(300L);
        objectMapper = new ObjectMapper();
        tool = new EmailTool(emailCapability, objectMapper);
    }

    @Test
    void definitionDescribesTwoStepApproval() {
        assertThat(tool.name()).isEqualTo("send_email");
        assertThat(tool.description())
                .contains("prepare", "确认发送", "confirm", "clarify");
        assertThat(tool.inputType()).isEqualTo(EmailToolInput.class);
        assertThat(tool.returnDirect()).isTrue();
    }

    @Test
    void preparesDraftWithoutDirectDelivery() {
        EmailMessage request = new EmailMessage(
                List.of("friend@example.com"),
                "问候",
                "最近好吗？");
        when(emailCapability.prepareEmail(
                TOOL_CONTEXT.conversationId(),
                TOOL_CONTEXT.currentUserText(),
                TOOL_CONTEXT.emailSendRequested(),
                request)).thenReturn(request);
        EmailToolInput input = new EmailToolInput(
                "prepare",
                List.of("friend@example.com"),
                "问候",
                "最近好吗？");

        ToolExecution execution = tool.execute(input, TOOL_CONTEXT);

        assertThat(execution.content()).contains("\"status\":\"approval_required\"");
        verify(emailCapability).prepareEmail(
                TOOL_CONTEXT.conversationId(),
                TOOL_CONTEXT.currentUserText(),
                TOOL_CONTEXT.emailSendRequested(),
                request);
    }

    @Test
    void confirmationUsesOnlyServerSidePendingDraft() {
        ToolContext confirmationContext = new ToolContext(
                TOOL_CONTEXT.conversationId(),
                "确认发送");
        EmailMessage request = new EmailMessage(
                List.of("friend@example.com"),
                "问候",
                "最近好吗？");
        when(emailCapability.confirmAndSendEmail(
                confirmationContext.conversationId(),
                confirmationContext.currentUserText())).thenReturn(request);
        EmailToolInput input = new EmailToolInput(
                "confirm",
                List.of(),
                null,
                null);

        ToolExecution execution = tool.execute(input, confirmationContext);

        assertThat(execution.content()).contains("\"status\":\"sent\"");
        verify(emailCapability).confirmAndSendEmail(
                confirmationContext.conversationId(),
                confirmationContext.currentUserText());
    }

    @Test
    void includesRecentGeneratedFileWhenUserRequestsAttachment() {
        GeneratedMedia pdf = new GeneratedMedia(
                GeneratedMediaKind.FILE,
                new byte[] {1, 2, 3},
                "西湖行程.pdf",
                0L,
                "");
        ToolContext attachmentContext = new ToolContext(
                TOOL_CONTEXT.conversationId(),
                "请把刚才的 PDF 作为附件发给 friend@example.com",
                true,
                false,
                false,
                true,
                List.of(pdf));
        when(emailCapability.prepareEmail(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(EmailMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(3));

        ToolExecution execution = tool.execute(
                new EmailToolInput(
                        "prepare",
                        List.of("friend@example.com"),
                        "西湖行程",
                        "详见附件。"),
                attachmentContext);

        assertThat(execution.content()).contains("西湖行程.pdf");
        org.mockito.ArgumentCaptor<EmailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailCapability).prepareEmail(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(true),
                captor.capture());
        assertThat(captor.getValue().attachments()).hasSize(1);
        assertThat(captor.getValue().attachments().getFirst().fileName()).isEqualTo("西湖行程.pdf");
    }

    @Test
    void attachesGeneratedSpreadsheetWithTheXlsxMediaType() {
        GeneratedMedia spreadsheet = new GeneratedMedia(
                GeneratedMediaKind.FILE,
                new byte[] {1, 2, 3},
                "本周任务.xlsx",
                0L,
                "");
        ToolContext attachmentContext = new ToolContext(
                TOOL_CONTEXT.conversationId(),
                "请把生成的 Excel 作为附件发给 friend@example.com",
                true,
                false,
                false,
                true,
                List.of(spreadsheet));
        when(emailCapability.prepareEmail(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.any(EmailMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(3));

        tool.execute(
                new EmailToolInput(
                        "prepare",
                        List.of("friend@example.com"),
                        "本周任务",
                        "详见附件。"),
                attachmentContext);

        org.mockito.ArgumentCaptor<EmailMessage> captor =
                org.mockito.ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailCapability).prepareEmail(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(true),
                captor.capture());
        assertThat(captor.getValue().attachments()).singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.fileName()).isEqualTo("本周任务.xlsx");
                    assertThat(attachment.contentType()).isEqualTo(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                });
    }
}
