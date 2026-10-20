package com.weida.bot.wechatilinkbot.channel.ilink;

import com.weida.bot.wechatilinkbot.channel.WechatChannel;
import com.weida.bot.wechatilinkbot.infrastructure.desktop.DesktopBrowserGateway;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring application events to the WeChat Bot session lifecycle.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class IlinkLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkLifecycle.class);
    private static final String LOGIN_PATH = "/wechat/login";

    private final WechatChannel wechatChannel;
    private final IlinkProperties properties;
    private final DesktopBrowserGateway browserGateway;

    public IlinkLifecycle(
            WechatChannel wechatChannel,
            IlinkProperties properties,
            DesktopBrowserGateway browserGateway) {
        this.wechatChannel = wechatChannel;
        this.properties = properties;
        this.browserGateway = browserGateway;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWechatBot(ApplicationReadyEvent event) {
        wechatChannel.start();
        if (!properties.isEnabled() || !properties.isAutoOpenLoginPage()) {
            return;
        }

        URI loginPage = loginPageUri(event.getApplicationContext());
        try {
            if (!browserGateway.available()) {
                LOG.info(
                        "wechat_login_page_auto_open_skipped reason=desktop_unavailable url={}",
                        loginPage);
                return;
            }
            browserGateway.open(loginPage);
            LOG.info("wechat_login_page_auto_opened url={}", loginPage);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "wechat_login_page_auto_open_failed url={} reason={}",
                    loginPage,
                    exception.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopWechatBot() {
        wechatChannel.stop();
    }

    static URI loginPageUri(ApplicationContext context) {
        int port = 8080;
        if (context instanceof WebServerApplicationContext webServerContext
                && webServerContext.getWebServer() != null) {
            port = webServerContext.getWebServer().getPort();
        }
        String host = context.getEnvironment().getProperty("server.address", "127.0.0.1");
        host = browserHost(host);
        String scheme = Boolean.parseBoolean(
                context.getEnvironment().getProperty("server.ssl.enabled", "false"))
                ? "https"
                : "http";
        return URI.create(scheme + "://" + host + ":" + port + LOGIN_PATH);
    }

    private static String browserHost(String configuredHost) {
        String host = configuredHost == null || configuredHost.isBlank()
                ? "127.0.0.1"
                : configuredHost.strip();
        if (host.equals("0.0.0.0") || host.equals("::") || host.equals("[::]")) {
            return "127.0.0.1";
        }
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }
}
