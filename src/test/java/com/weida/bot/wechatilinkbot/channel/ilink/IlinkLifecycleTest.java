package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.weida.bot.wechatilinkbot.channel.WechatChannel;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.DesktopBrowserGateway;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

class IlinkLifecycleTest {

    @Test
    void startsTheChannelAndOpensTheLocalLoginPageWhenEnabled() {
        WechatChannel channel = mock(WechatChannel.class);
        DesktopBrowserGateway browser = mock(DesktopBrowserGateway.class);
        when(browser.available()).thenReturn(true);
        IlinkProperties properties = new IlinkProperties();
        ConfigurableApplicationContext context = applicationContext(
                "0.0.0.0", "false");
        IlinkLifecycle lifecycle = new IlinkLifecycle(channel, properties, browser);

        lifecycle.startWechatBot(readyEvent(context));

        verify(channel).start();
        verify(browser).open(URI.create("http://127.0.0.1:8080/wechat/login"));
    }

    @Test
    void doesNotOpenTheLoginPageWhenTheChannelIsDisabled() {
        WechatChannel channel = mock(WechatChannel.class);
        DesktopBrowserGateway browser = mock(DesktopBrowserGateway.class);
        IlinkProperties properties = new IlinkProperties();
        properties.setEnabled(false);
        IlinkLifecycle lifecycle = new IlinkLifecycle(channel, properties, browser);

        lifecycle.startWechatBot(readyEvent(applicationContext(
                "127.0.0.1", "false")));

        verify(channel).start();
        verifyNoInteractions(browser);
    }

    @Test
    void buildsTheLoginPageWithTheConfiguredHttpsAndIpv6Host() {
        ConfigurableApplicationContext context = applicationContext(
                "::1", "true");

        assertThat(IlinkLifecycle.loginPageUri(context))
                .isEqualTo(URI.create("https://[::1]:8080/wechat/login"));
    }

    @Test
    void usesTheActualBoundWebServerPort() {
        WebServerApplicationContext context = webServerApplicationContext(
                "127.0.0.1", "false", 18080);

        assertThat(IlinkLifecycle.loginPageUri(context))
                .isEqualTo(URI.create("http://127.0.0.1:18080/wechat/login"));
    }

    private static ApplicationReadyEvent readyEvent(
            ConfigurableApplicationContext context) {
        return new ApplicationReadyEvent(
                mock(SpringApplication.class),
                new String[0],
                context,
                Duration.ZERO);
    }

    private static ConfigurableApplicationContext applicationContext(
            String address,
            String sslEnabled) {
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        when(environment.getProperty("server.address", "127.0.0.1"))
                .thenReturn(address);
        when(environment.getProperty("server.ssl.enabled", "false"))
                .thenReturn(sslEnabled);
        when(context.getEnvironment()).thenReturn(environment);
        return context;
    }

    private static WebServerApplicationContext webServerApplicationContext(
            String address,
            String sslEnabled,
            int port) {
        WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
        WebServer webServer = mock(WebServer.class);
        when(environment.getProperty("server.address", "127.0.0.1"))
                .thenReturn(address);
        when(environment.getProperty("server.ssl.enabled", "false"))
                .thenReturn(sslEnabled);
        when(context.getEnvironment()).thenReturn(environment);
        when(context.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(port);
        return context;
    }
}
