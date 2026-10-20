package com.weida.bot.wechatilinkbot.capability;

/** Raised when an untrusted model or user value violates desktop-task policy. */
public class DesktopTaskPolicyViolationException extends IllegalArgumentException {

    public DesktopTaskPolicyViolationException(String message) {
        super(message);
    }
}
