package com.weida.bot.wechatilinkbot.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.capability.EmotionalProfileService;
import com.weida.bot.wechatilinkbot.model.AgentRequest;
import com.weida.bot.wechatilinkbot.model.ConversationMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPromptAssemblerTest {

    @Test
    void rendersCompressedHistoryAsUntrustedUserContext() {
        AgentProperties properties = new AgentProperties();
        EmotionalProfileService profileService = mock(EmotionalProfileService.class);
        when(profileService.getProfile("conv-1")).thenReturn("");
        when(profileService.isEnabled()).thenReturn(false);
        AgentPromptAssembler assembler = new AgentPromptAssembler(
                properties,
                profileService,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        List<ConversationMessage> history = List.of(
                new ConversationMessage(
                        null,
                        "conv-1",
                        ConversationContextCompactor.SUMMARY_ROLE,
                        "摘要内容",
                        now,
                        now),
                new ConversationMessage(
                        null,
                        "conv-1",
                        ConversationContextCompactor.HISTORY_ROLE,
                        "用户：原文内容 [本轮已发送图片]",
                        now,
                        now));

        List<ModelMessage> messages = assembler.assemble(
                history,
                new AgentRequest("conv-1", "继续", List.of()),
                List.of(),
                false);

        assertThat(messages).extracting(ModelMessage::role)
                .containsExactly(ModelRole.SYSTEM, ModelRole.USER, ModelRole.USER, ModelRole.USER);
        assertThat(messages.get(1).text())
                .startsWith(ConversationContextCompactor.SUMMARY_MARKER)
                .contains("摘要内容");
        assertThat(messages.get(2).text())
                .startsWith(ConversationContextCompactor.HISTORY_MARKER)
                .contains("[本轮已发送图片]");
    }

    @Test
    void givesTheModelAConcreteDocumentJsonContract() {
        AgentProperties properties = new AgentProperties();
        EmotionalProfileService profileService = mock(EmotionalProfileService.class);
        when(profileService.getProfile("conv-1")).thenReturn("");
        when(profileService.isEnabled()).thenReturn(false);
        AgentPromptAssembler assembler = new AgentPromptAssembler(
                properties,
                profileService,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC));

        ModelMessage systemMessage = assembler.assemble(
                        List.of(),
                        new AgentRequest("conv-1", "生成报告.docx", List.of()),
                        List.of(),
                        false)
                .getFirst();

        assertThat(systemMessage.text())
                .contains("【文档生成工具规则】")
                .contains("content 与 sectionsJson 互斥")
                .contains("合法示例")
                .contains("不要输出 Markdown 代码块");
    }
}
