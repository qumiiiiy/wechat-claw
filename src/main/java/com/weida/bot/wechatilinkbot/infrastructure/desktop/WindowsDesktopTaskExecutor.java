package com.weida.bot.wechatilinkbot.infrastructure.desktop;

import com.weida.bot.wechatilinkbot.model.DesktopTaskResult;
import java.net.URI;
import org.springframework.stereotype.Component;

/** Executes the small, validated set of V1 desktop actions. */
@Component
public class WindowsDesktopTaskExecutor {

    private final DesktopBrowserGateway browserGateway;

    public WindowsDesktopTaskExecutor(DesktopBrowserGateway browserGateway) {
        this.browserGateway = browserGateway;
    }

    public boolean available() {
        return browserGateway.available();
    }

    public DesktopTaskResult openUrl(URI url) {
        browserGateway.open(url);
        return DesktopTaskResult.succeeded("已在本机默认浏览器打开 " + url.getHost());
    }
}
