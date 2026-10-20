package com.weida.bot.wechatilinkbot.channel.ilink;

import com.weida.bot.wechatilinkbot.channel.WechatChannel;
import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Default WeChat channel service delegating SDK resource ownership to the session manager.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class IlinkWechatChannel implements WechatChannel {

    private final IlinkSessionRegistry sessionRegistry;

    public IlinkWechatChannel(IlinkSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void start() {
        sessionRegistry.start();
    }

    @Override
    public WechatSession createSession() {
        return sessionRegistry.createSession();
    }

    @Override
    public List<WechatSession> listSessions() {
        return sessionRegistry.listSessions();
    }

    @Override
    public WechatSession getLoginStatus(String sessionId) {
        return sessionRegistry.getLoginStatus(sessionId);
    }

    @Override
    public WechatSession refreshLogin(String sessionId) {
        return sessionRegistry.refreshLogin(sessionId);
    }

    @Override
    public void removeSession(String sessionId) {
        sessionRegistry.removeSession(sessionId);
    }

    @Override
    public byte[] getQrCodePng(String sessionId) throws IOException {
        return sessionRegistry.getQrCodePng(sessionId);
    }

    @Override
    public void stop() {
        sessionRegistry.stop();
    }
}
