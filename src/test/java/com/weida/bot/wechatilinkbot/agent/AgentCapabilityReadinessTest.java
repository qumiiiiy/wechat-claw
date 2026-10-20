package com.weida.bot.wechatilinkbot.agent;

import static com.weida.bot.wechatilinkbot.tool.ToolNames.BROWSE_FILES;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_DIAGRAM;
import static com.weida.bot.wechatilinkbot.tool.ToolNames.GENERATE_PRESENTATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

class AgentCapabilityReadinessTest {

    @Test
    void explainsHowToEnableFileBrowseWithoutGrantingAPath() {
        ToolRegistry registry = unavailableRegistry("allowed_paths_missing");

        String reply = AgentCapabilityReadiness.unavailableReply(
                registry,
                BROWSE_FILES,
                "文件搜索当前不可用。");

        assertThat(reply).contains("BOT_FILE_BROWSE_ALLOWED_PATHS");
        assertThat(reply).doesNotContain("D:\\");
    }

    @Test
    void explainsThatPlantUmlNeedsAnExecutableFile() {
        ToolRegistry registry = unavailableRegistry("executable_missing");

        String reply = AgentCapabilityReadiness.unavailableReply(
                registry,
                GENERATE_DIAGRAM,
                "PlantUML 图表当前不可用。");

        assertThat(reply)
                .contains("PLANTUML_EXECUTABLE")
                .contains("plantuml.bat/plantuml");
    }

    @Test
    void explainsThePptxOptInSwitch() {
        ToolRegistry registry = unavailableRegistry("office_presentation_unavailable");

        String reply = AgentCapabilityReadiness.unavailableReply(
                registry,
                GENERATE_PRESENTATION,
                "PPTX 演示文稿当前不可用。");

        assertThat(reply).contains("BOT_PPTX_ENABLED=true");
    }

    private static ToolRegistry unavailableRegistry(String reason) {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.availabilityReason(anyString())).thenReturn(reason);
        when(registry.available(anyString())).thenReturn(false);
        return registry;
    }
}
