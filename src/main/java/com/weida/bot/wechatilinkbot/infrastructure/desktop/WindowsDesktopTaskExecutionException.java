package com.weida.bot.wechatilinkbot.infrastructure.desktop;

/** Stable failure raised by a local Windows desktop action. */
public class WindowsDesktopTaskExecutionException extends RuntimeException {

    public WindowsDesktopTaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
