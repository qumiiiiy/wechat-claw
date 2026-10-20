package com.weida.bot.wechatilinkbot.channel;

/**
 * Indicates that a requested WeChat Bot session is not registered.
 *
 * @author ryx
 * @since 2026-07-20
 */
public class WechatSessionNotFoundException extends RuntimeException {

    public WechatSessionNotFoundException(String sessionId) {
        super("微信 Bot 会话不存在：" + sessionId);
    }
}
