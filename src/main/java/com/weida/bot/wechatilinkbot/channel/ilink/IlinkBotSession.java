package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.exception.SessionExpiredException;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.login.LoginStatus;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import com.google.zxing.WriterException;
import com.weida.bot.wechatilinkbot.channel.WechatChannelNotReadyException;
import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the SDK client, login state, and polling loop for one WeChat Bot.
 */
final class IlinkBotSession {

    private static final Logger LOG = LoggerFactory.getLogger(IlinkBotSession.class);
    private static final long MINIMUM_SLEEP_MILLIS = 100L;

    private final String sessionId;
    private final IlinkProperties properties;
    private final ILinkConfig iLinkConfig;
    private final IlinkClientFactory clientFactory;
    private final IlinkMessageDispatcher messageDispatcher;
    private final QrCodeRenderer qrCodeRenderer;
    private final ExecutorService pollingExecutor;
    private final BooleanSupplier registryRunning;
    private final Instant createdAt = Instant.now();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean pollerStarted = new AtomicBoolean();
    private final AtomicLong receivedMessageCount = new AtomicLong();

    private volatile ILinkClient client;
    private volatile String qrCodeContent;
    private volatile Instant qrCodeGeneratedAt;
    private volatile Instant lastPollStartedAt;
    private volatile Instant lastPollCompletedAt;
    private volatile Instant lastMessageReceivedAt;
    private volatile String lastError;

    IlinkBotSession(
            String sessionId,
            IlinkProperties properties,
            ILinkConfig iLinkConfig,
            IlinkClientFactory clientFactory,
            IlinkMessageDispatcher messageDispatcher,
            QrCodeRenderer qrCodeRenderer,
            ExecutorService pollingExecutor,
            BooleanSupplier registryRunning) {
        this.sessionId = sessionId;
        this.properties = properties;
        this.iLinkConfig = iLinkConfig;
        this.clientFactory = clientFactory;
        this.messageDispatcher = messageDispatcher;
        this.qrCodeRenderer = qrCodeRenderer;
        this.pollingExecutor = pollingExecutor;
        this.registryRunning = registryRunning;
    }

    synchronized void refreshLogin() {
        ensureOpen();
        ILinkClient previousClient = client;
        client = null;
        closeClient(previousClient);
        qrCodeContent = null;
        qrCodeGeneratedAt = null;
        ILinkClient newClient = buildClient();
        client = newClient;
        executeLogin(newClient);
        LOG.info(
                "wechat_qr_code_generated sessionRef={} loginPage=/wechat/login",
                IlinkLogReference.of(sessionId));
    }

    void startPolling() {
        if (pollerStarted.compareAndSet(false, true)) {
            pollingExecutor.execute(this::poll);
        }
    }

    WechatSession getStatus() {
        ILinkClient activeClient = client;
        LoginStatus loginStatus = activeClient == null
                ? null
                : activeClient.getLoginStatus();
        LoginContext context = activeClient == null
                ? null
                : activeClient.getLoginContext();
        return new WechatSession(
                sessionId,
                properties.isEnabled(),
                loginStatus == null ? "NOT_STARTED" : loginStatus.getStatus().name(),
                activeClient == null
                        ? "NOT_CONNECTED"
                        : activeClient.getConnectionStatus().name(),
                context == null ? null : context.getBotId(),
                context == null ? null : context.getUserId(),
                qrCodeContent != null,
                createdAt,
                qrCodeGeneratedAt,
                lastPollStartedAt,
                lastPollCompletedAt,
                lastMessageReceivedAt,
                receivedMessageCount.get(),
                lastError);
    }

    byte[] getQrCodePng() throws IOException {
        String content = qrCodeContent;
        if (content == null || content.isBlank()) {
            throw new WechatChannelNotReadyException(
                    "登录二维码尚未生成，请刷新该 Bot 的二维码");
        }
        try {
            return qrCodeRenderer.generatePng(content, properties.getQrCodeSize());
        } catch (WriterException exception) {
            throw new IOException("生成登录二维码图片失败", exception);
        }
    }

    ILinkClient requireLoggedInClient() {
        ILinkClient activeClient = client;
        if (activeClient == null || !activeClient.isLoggedIn()) {
            throw new WechatChannelNotReadyException(
                    "微信 Bot 尚未登录，sessionId=" + sessionId);
        }
        return activeClient;
    }

    String getCurrentBotId() {
        ILinkClient activeClient = client;
        LoginContext context = activeClient == null
                ? null
                : activeClient.getLoginContext();
        return context == null ? null : context.getBotId();
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ILinkClient activeClient = client;
        client = null;
        closeClient(activeClient);
    }

    private void executeLogin(ILinkClient newClient) {
        try {
            qrCodeContent = newClient.executeLogin();
            qrCodeGeneratedAt = Instant.now();
            lastError = null;
        } catch (RuntimeException exception) {
            lastError = rootMessage(exception);
            client = null;
            closeClient(newClient);
            throw new WechatChannelNotReadyException(
                    "生成微信登录二维码失败：" + lastError);
        }
    }

    private ILinkClient buildClient() {
        AtomicReference<ILinkClient> owner = new AtomicReference<>();
        ILinkClient newClient = clientFactory.create(
                iLinkConfig,
                null,
                loginListener(owner),
                messageList -> handleIncomingMessages(owner.get(), messageList));
        owner.set(newClient);
        return newClient;
    }

    private OnLoginListener loginListener(AtomicReference<ILinkClient> owner) {
        return new OnLoginListener() {
            @Override
            public void onLoginSuccess(LoginContext context) {
                if (!isActive(owner.get())) {
                    return;
                }
                lastError = null;
                LOG.info(
                        "wechat_login_succeeded sessionRef={} botRef={} userRef={}",
                        IlinkLogReference.of(sessionId),
                        IlinkLogReference.of(context.getBotId()),
                        IlinkLogReference.of(context.getUserId()));
            }

            @Override
            public void onLoginFailure(Throwable throwable) {
                if (!isActive(owner.get()) || isCancellation(throwable)) {
                    return;
                }
                lastError = rootMessage(throwable);
                LOG.error(
                        "wechat_login_failed sessionRef={}",
                        IlinkLogReference.of(sessionId),
                        throwable);
            }
        };
    }

    private void handleIncomingMessages(
            ILinkClient sourceClient,
            List<WeixinMessage> messageList) {
        if (!isActive(sourceClient) || messageList == null || messageList.isEmpty()) {
            return;
        }
        lastMessageReceivedAt = Instant.now();
        receivedMessageCount.addAndGet(messageList.size());
        messageDispatcher.dispatch(sourceClient, messageList);
    }

    private void poll() {
        while (registryRunning.getAsBoolean()
                && !closed.get()
                && !Thread.currentThread().isInterrupted()) {
            ILinkClient activeClient = client;
            if (activeClient == null || !activeClient.isLoggedIn()) {
                sleep(properties.getPollInterval().toMillis());
                continue;
            }
            try {
                lastPollStartedAt = Instant.now();
                activeClient.getUpdates();
                lastPollCompletedAt = Instant.now();
                lastError = null;
                sleep(properties.getPollInterval().toMillis());
            } catch (IOException | RuntimeException exception) {
                handlePollingFailure(activeClient, exception);
            }
        }
    }

    private void handlePollingFailure(ILinkClient failedClient, Throwable throwable) {
        if (!isActive(failedClient)) {
            return;
        }
        lastError = rootMessage(throwable);
        if (hasCause(throwable, SessionExpiredException.class)) {
            refreshExpiredSession();
        } else {
            LOG.error(
                    "wechat_message_poll_failed sessionRef={} retryDelay={}",
                    IlinkLogReference.of(sessionId),
                    properties.getPollFailureDelay(),
                    throwable);
        }
        sleep(properties.getPollFailureDelay().toMillis());
    }

    private void refreshExpiredSession() {
        LOG.warn(
                "wechat_session_expired sessionRef={} action=refresh_login",
                IlinkLogReference.of(sessionId));
        try {
            refreshLogin();
        } catch (RuntimeException exception) {
            lastError = rootMessage(exception);
            LOG.error(
                    "wechat_session_refresh_failed sessionRef={}",
                    IlinkLogReference.of(sessionId),
                    exception);
        }
    }

    private boolean isActive(ILinkClient candidate) {
        return registryRunning.getAsBoolean()
                && !closed.get()
                && candidate != null
                && candidate == client;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new WechatChannelNotReadyException("微信 Bot 会话已关闭");
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, MINIMUM_SLEEP_MILLIS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private static boolean hasCause(
            Throwable throwable,
            Class<? extends Throwable> exceptionType) {
        Throwable current = throwable;
        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isCancellation(Throwable throwable) {
        return hasCause(throwable, CancellationException.class);
    }

    private static void closeClient(ILinkClient client) {
        if (client != null) {
            client.close();
        }
    }
}
