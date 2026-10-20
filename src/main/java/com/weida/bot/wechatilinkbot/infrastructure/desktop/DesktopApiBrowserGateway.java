package com.weida.bot.wechatilinkbot.infrastructure.desktop;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import org.springframework.stereotype.Component;

/** Windows desktop adapter backed by the standard Java Desktop API. */
@Component
public class DesktopApiBrowserGateway implements DesktopBrowserGateway {

    @Override
    public boolean available() {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        try {
            return Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
        } catch (HeadlessException | SecurityException exception) {
            return false;
        }
    }

    @Override
    public void open(URI url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new WindowsDesktopTaskExecutionException(
                        "当前运行环境不支持打开浏览器，请使用 -Djava.awt.headless=false 启动", null);
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new WindowsDesktopTaskExecutionException("当前桌面环境不支持打开浏览器", null);
            }
            desktop.browse(url);
        } catch (IOException | HeadlessException | SecurityException exception) {
            throw new WindowsDesktopTaskExecutionException("本机浏览器打开失败", exception);
        }
    }
}
