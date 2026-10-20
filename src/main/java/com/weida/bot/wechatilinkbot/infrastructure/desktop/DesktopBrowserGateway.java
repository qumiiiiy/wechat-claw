package com.weida.bot.wechatilinkbot.infrastructure.desktop;

import java.net.URI;

/** Narrow adapter for opening an approved URL in the local interactive desktop session. */
public interface DesktopBrowserGateway {

    boolean available();

    void open(URI url);
}
