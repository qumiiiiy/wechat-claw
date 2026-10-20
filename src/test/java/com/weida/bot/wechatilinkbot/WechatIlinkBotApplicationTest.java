package com.weida.bot.wechatilinkbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.weida.bot.wechatilinkbot.infrastructure.desktop.DesktopBrowserGateway;
import com.weida.bot.wechatilinkbot.persistence.ConversationMemory;
import com.weida.bot.wechatilinkbot.persistence.SqliteConversationMemory;
import com.weida.bot.wechatilinkbot.tool.ToolRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(properties = {
        "bot.channel.wechat.enabled=false",
        "bot.persistence.conversation.type=sqlite",
        "bot.persistence.conversation.sqlite.jdbc-url=jdbc:sqlite:./target/test-data/application.db",
        "bot.capability.desktop.enabled=true"
})
@Import(WechatIlinkBotApplicationTest.DesktopTestConfiguration.class)
class WechatIlinkBotApplicationTest {

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void contextLoads() {
    }

    @Test
    void sqliteConfigurationProvidesSqliteImplementation() {
        assertThat(conversationMemory)
                .isInstanceOf(SqliteConversationMemory.class);
    }

    @Test
    void desktopTaskToolsAreRegisteredOnlyForAnExplicitIntent() {
        assertThat(toolRegistry.available("request_open_url")).isTrue();
        assertThat(toolRegistry.listDefinitions(true, java.util.Set.of()))
                .extracting(definition -> definition.name())
                .doesNotContain("request_open_url");
        assertThat(toolRegistry.listDefinitions(true, java.util.Set.of("request_open_url")))
                .extracting(definition -> definition.name())
                .contains("request_open_url");
    }

    @Test
    void qrCodeToolIsRegisteredOnlyForAnExplicitIntent() {
        assertThat(toolRegistry.available("generate_qr_code")).isTrue();
        assertThat(toolRegistry.listDefinitions(true, java.util.Set.of()))
                .extracting(definition -> definition.name())
                .doesNotContain("generate_qr_code");
        assertThat(toolRegistry.listDefinitions(true, java.util.Set.of("generate_qr_code")))
                .extracting(definition -> definition.name())
                .contains("generate_qr_code");
    }

    @Test
    void genericChatCannotSeeSpecializedOrSideEffectingTools() {
        assertThat(toolRegistry.listDefinitions(true, java.util.Set.of()))
                .extracting(definition -> definition.name())
                .doesNotContain(
                        "generate_qr_code",
                        "generate_image",
                        "generate_text_file",
                        "generate_document",
                        "generate_diagram",
                        "generate_spreadsheet",
                        "read_spreadsheet",
                        "append_spreadsheet",
                        "send_email",
                        "learning_query",
                        "request_open_url",
                        "browse_files",
                        "select_file");
    }

    @Test
    void loginPageIsServedFromStaticResources() throws IOException {
        ClassPathResource html = new ClassPathResource("static/wechat/login.html");
        ClassPathResource css = new ClassPathResource("static/wechat/css/login.css");
        ClassPathResource javascript = new ClassPathResource("static/wechat/js/login.js");
        ClassPathResource icon = new ClassPathResource("static/wechat/fig/wechat.png");

        assertThat(html.exists()).isTrue();
        assertThat(css.exists()).isTrue();
        assertThat(javascript.exists()).isTrue();
        assertThat(icon.exists()).isTrue();
        assertThat(html.getContentAsString(StandardCharsets.UTF_8))
                .contains("/wechat/css/login.css")
                .contains("/wechat/js/login.js")
                .contains("/wechat/fig/wechat.png")
                .contains("id=\"status-notification\"")
                .contains("id=\"bot-select\"");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DesktopTestConfiguration {

        @Bean
        @Primary
        DesktopBrowserGateway availableDesktopBrowserGateway() {
            return new DesktopBrowserGateway() {

                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public void open(URI url) {
                    throw new UnsupportedOperationException("测试不会执行桌面动作");
                }
            };
        }
    }
}
