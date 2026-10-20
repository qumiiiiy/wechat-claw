package com.weida.bot.wechatilinkbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.weida.bot.wechatilinkbot.capability.OcsStudyCapability;
import com.weida.bot.wechatilinkbot.channel.web.OcsBridgeController;
import com.weida.bot.wechatilinkbot.tool.ToolDefinition;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "bot.channel.wechat.enabled=false",
        "bot.persistence.conversation.type=memory",
        "bot.infrastructure.learning.enabled=false",
        "bot.capability.learning.ocs.enabled=true"
})
class OcsBridgeApplicationTest {

    @Autowired
    private OcsStudyCapability studyCapability;

    @Autowired
    private OcsBridgeController bridgeController;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersOnlyExplicitOcsToolsAndCompanionResource() throws IOException {
        assertThat(studyCapability.available()).isTrue();
        assertThat(bridgeController).isNotNull();
        assertThat(toolRegistry.available("start_ocs_study")).isTrue();
        assertThat(toolRegistry.available("get_ocs_study_status")).isTrue();
        assertThat(toolRegistry.listDefinitions(true, Set.of()))
                .extracting(definition -> definition.name())
                .doesNotContain("start_ocs_study", "get_ocs_study_status");
        assertThat(toolRegistry.listDefinitions(true, Set.of("start_ocs_study")))
                .extracting(definition -> definition.name())
                .contains("start_ocs_study");
        ToolDefinition startDefinition = toolRegistry
                .listDefinitions(true, Set.of("start_ocs_study"))
                .stream()
                .filter(definition -> "start_ocs_study".equals(definition.name()))
                .findFirst()
                .orElseThrow();
        assertThat(startDefinition.inputSchema())
                .contains("\"courseReference\"")
                .contains("\"chapterName\"")
                .contains("\"required\":[\"courseName\"]");

        ClassPathResource companion =
                new ClassPathResource("static/ocs/wechat-claw-ocs-bridge.user.js");
        ClassPathResource launchPage = new ClassPathResource("static/ocs/launch.html");
        assertThat(companion.exists()).isTrue();
        assertThat(launchPage.exists()).isTrue();
        String companionScript = companion.getContentAsString(StandardCharsets.UTF_8);
        String launchPageContent = launchPage.getContentAsString(StandardCharsets.UTF_8);
        assertThat(companionScript)
                .contains("@connect      127.0.0.1")
                .contains("X-Wechat-Claw-Ocs-Client")
                .contains("companion-v1")
                .contains("wechat-claw-ocs-question")
                .contains("wechat-claw-ocs-answer")
                .contains("GM_getTab")
                .contains("GM_saveTab")
                .contains("GM_setValue")
                .contains("detectOcsPlugin")
                .contains("injectOcsConfig")
                .contains("extractQrFromLoginPage")
                .contains("pollLoginCompletion")
                .contains("cx.new.study.restudy")
                .contains("cx.new.study.mode")
                .contains("common.settings.answererWrappers")
                .contains("wechat-claw-ocs-control")
                .contains("reportCompanionReady")
                .contains("/companion/ready")
                .contains("COMPANION_VERSION = '0.17.2'")
                .contains("selectChapterIfNeeded")
                .contains("chapterName")
                .contains("isChaoxingLoginPage()")
                .contains("\\u5B66\\u4E60\\u63A7\\u5236\\u53F0")
                .contains("response.status")
                .doesNotContain("document.cookie")
                .doesNotContain("bridgeToken")
                .doesNotContain("/commands/next")
                .doesNotContain("BOT_OCS_BRIDGE_TOKEN");
        assertThat(launchPageContent)
                .contains("OCS Bridge")
                .contains("OCS")
                .contains("href=\"/ocs/wechat-claw-ocs-bridge.user.js\"");
    }

    @Test
    void doesNotGrantCorsAccessToTheLocalBridge() throws Exception {
        mockMvc.perform(options("/api/local/ocs/launch/command")
                        .header(HttpHeaders.ORIGIN, "https://courses.example.edu.cn")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "X-Wechat-Claw-Ocs-Client"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
