package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_SPREADSHEET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weida.bot.wechatilinkbot.agent.intent.DesktopTaskOperation;
import com.weida.bot.wechatilinkbot.capability.MediaCapability;
import com.weida.bot.wechatilinkbot.model.AgentReply;
import com.weida.bot.wechatilinkbot.model.VoicePreference;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import com.weida.bot.wechatilinkbot.tool.ToolRunContext;
import org.junit.jupiter.api.Test;

class AgentResponsePolicyTest {

    @Test
    void reportsUnavailableSpreadsheetWithoutClaimingFileGeneration() throws Exception {
        MediaCapability mediaCapability = mock(MediaCapability.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        AgentPromptAssembler promptAssembler = mock(AgentPromptAssembler.class);
        when(promptAssembler.sanitize(anyString()))
                .thenReturn("工作簿已经生成");
        when(toolRegistry.available(GENERATE_SPREADSHEET)).thenReturn(false);
        AgentResponsePolicy policy = new AgentResponsePolicy(
                mediaCapability,
                toolRegistry,
                new ObjectMapper(),
                promptAssembler);

        AgentReply reply = policy.finish(
                "工作簿已经生成",
                runContext(),
                spreadsheetIntent(),
                "conversation");

        assertThat(reply.text())
                .contains("Excel 工作簿没有成功生成")
                .doesNotContain("已经生成");
        assertThat(reply.generatedMedia()).isEmpty();
        verify(toolRegistry).available(GENERATE_SPREADSHEET);
    }

    private static ToolRunContext runContext() {
        return new ToolRunContext(
                "wechat-ilink|user-1",
                "生成一份 Excel",
                false,
                false,
                1,
                new ObjectMapper());
    }

    private static AgentIntent spreadsheetIntent() {
        return new AgentIntent(
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                VoicePreference.DEFAULT,
                DesktopTaskOperation.NONE);
    }
}
