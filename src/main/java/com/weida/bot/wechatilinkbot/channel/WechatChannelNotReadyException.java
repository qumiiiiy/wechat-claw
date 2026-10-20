package com.weida.bot.wechatilinkbot.channel;

/**
 * Indicates that a requested WeChat Bot operation cannot run in its current state.
 *
 * @author ryx
 * @since 2026-07-20
 */
public class WechatChannelNotReadyException extends RuntimeException {

    public WechatChannelNotReadyException(String message) {
        super(message);
    }
}
