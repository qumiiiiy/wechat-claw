package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.weida.bot.wechatilinkbot.channel.WechatChannelNotReadyException;
import com.weida.bot.wechatilinkbot.channel.WechatSessionNotFoundException;
import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Owns the lifecycle and SDK resources of all independent WeChat Bot sessions.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Component
public class IlinkSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkSessionRegistry.class);
    private final IlinkProperties properties;
    private final ILinkConfig iLinkConfig;
    private final IlinkClientFactory clientFactory;
    private final IlinkMessageDispatcher messageDispatcher;
    private final QrCodeRenderer qrCodeRenderer;
    private final ExecutorService pollingExecutor;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConcurrentHashMap<String, IlinkBotSession> sessionMap =
            new ConcurrentHashMap<>();

    public IlinkSessionRegistry(
            IlinkProperties properties,
            ILinkConfig iLinkConfig,
            IlinkClientFactory clientFactory,
            IlinkMessageDispatcher messageDispatcher,
            QrCodeRenderer qrCodeRenderer,
            @Qualifier("wechatMessagePollingExecutor") ExecutorService pollingExecutor) {
        this.properties = properties;
        this.iLinkConfig = iLinkConfig;
        this.clientFactory = clientFactory;
        this.messageDispatcher = messageDispatcher;
        this.qrCodeRenderer = qrCodeRenderer;
        this.pollingExecutor = pollingExecutor;
    }

    /** Starts the session registry when the WeChat channel is enabled. */
    public void start() {
        if (!properties.isEnabled()) {
            LOG.info("wechat_bot_disabled property=bot.channel.wechat.enabled");
            return;
        }
        running.set(true);
        LOG.info("wechat_bot_registry_started");
    }

    /** Creates one independent QR-login session. */
    public synchronized WechatSession createSession() {
        ensureEnabled();
        running.set(true);
        ensureSessionCapacity();
        String sessionId = UUID.randomUUID().toString();
        IlinkBotSession session = new IlinkBotSession(
                sessionId,
                properties,
                iLinkConfig,
                clientFactory,
                messageDispatcher,
                qrCodeRenderer,
                pollingExecutor,
                running::get);
        sessionMap.put(sessionId, session);
        try {
            session.startPolling();
            session.refreshLogin();
            LOG.info(
                    "wechat_bot_session_created sessionRef={}",
                    IlinkLogReference.of(sessionId));
            return session.getStatus();
        } catch (RuntimeException exception) {
            sessionMap.remove(sessionId, session);
            session.close();
            throw exception;
        }
    }

    /** Lists all sessions in creation order. */
    public List<WechatSession> listSessions() {
        return sessionMap.values().stream()
                .map(IlinkBotSession::getStatus)
                .sorted(Comparator.comparing(WechatSession::createdAt))
                .toList();
    }

    /** Gets one session's current state. */
    public WechatSession getLoginStatus(String sessionId) {
        return requireSession(sessionId).getStatus();
    }

    /** Refreshes only the selected session's SDK client and QR code. */
    public WechatSession refreshLogin(String sessionId) {
        IlinkBotSession session = requireSession(sessionId);
        session.refreshLogin();
        return session.getStatus();
    }

    /** Removes and closes one session. */
    public void removeSession(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId");
        IlinkBotSession session = sessionMap.remove(safeSessionId);
        if (session == null) {
            throw new WechatSessionNotFoundException(safeSessionId);
        }
        String botId = session.getCurrentBotId();
        session.close();
        LOG.info(
                "wechat_bot_session_removed sessionRef={} botRef={}",
                IlinkLogReference.of(safeSessionId),
                IlinkLogReference.of(botId));
    }

    /** Renders one session's current QR code. */
    public byte[] getQrCodePng(String sessionId) throws IOException {
        return requireSession(sessionId).getQrCodePng();
    }

    /** Stops and closes every active session. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        List<IlinkBotSession> activeSessionList = List.copyOf(sessionMap.values());
        sessionMap.clear();
        activeSessionList.forEach(IlinkBotSession::close);
        LOG.info("wechat_bot_registry_stopped sessionCount={}", activeSessionList.size());
    }

    private IlinkBotSession requireSession(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId");
        IlinkBotSession session = sessionMap.get(safeSessionId);
        if (session == null) {
            throw new WechatSessionNotFoundException(safeSessionId);
        }
        return session;
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new WechatChannelNotReadyException("微信 iLink Bot 未启用");
        }
    }

    private void ensureSessionCapacity() {
        if (sessionMap.size() >= properties.getMaxBotSessions()) {
            throw new WechatChannelNotReadyException(
                    "Bot 会话数量已达到上限：" + properties.getMaxBotSessions());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.strip();
    }

}
