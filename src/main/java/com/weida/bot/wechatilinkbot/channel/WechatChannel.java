package com.weida.bot.wechatilinkbot.channel;

import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.io.IOException;
import java.util.List;

/**
 * Manages WeChat Bot login sessions and outbound channel messages.
 *
 * @author ryx
 * @since 2026-07-20
 */
public interface WechatChannel {

    /** Starts the configured Bot session registry. */
    void start();

    /**
     * Creates a new independent QR-login session.
     *
     * @return initial session state
     */
    WechatSession createSession();

    /**
     * Lists all active Bot sessions in creation order.
     *
     * @return immutable session snapshot
     */
    List<WechatSession> listSessions();

    /**
     * Gets one Bot session.
     *
     * @param sessionId session identifier
     * @return current session state
     */
    WechatSession getLoginStatus(String sessionId);

    /**
     * Replaces only the selected session's SDK client and QR code.
     *
     * @param sessionId session identifier
     * @return refreshed session state
     */
    WechatSession refreshLogin(String sessionId);

    /**
     * Removes and closes one Bot session.
     *
     * @param sessionId session identifier
     */
    void removeSession(String sessionId);

    /**
     * Renders the selected session's QR code as PNG.
     *
     * @param sessionId session identifier
     * @return PNG bytes
     * @throws IOException when rendering fails
     */
    byte[] getQrCodePng(String sessionId) throws IOException;

    /** Stops and closes every managed Bot session. */
    void stop();
}
