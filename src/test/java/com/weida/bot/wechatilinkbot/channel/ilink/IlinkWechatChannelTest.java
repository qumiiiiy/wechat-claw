package com.weida.bot.wechatilinkbot.channel.ilink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.context.ResumeContext;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginStatus;
import com.github.wechat.ilink.sdk.core.state.ConnectionStatus;
import com.weida.bot.wechatilinkbot.channel.WechatChannel;
import com.weida.bot.wechatilinkbot.model.WechatSession;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class IlinkWechatChannelTest {

    @Test
    void refreshAndRemovalOnlyCloseTheSelectedBotSession() {
        IlinkProperties properties = new IlinkProperties();
        ILinkConfig config = ILinkConfig.builder().heartbeatEnabled(false).build();
        IlinkClientFactory clientFactory = mock(IlinkClientFactory.class);
        IlinkMessageDispatcher dispatcher = mock(IlinkMessageDispatcher.class);
        QrCodeRenderer qrCodeRenderer = mock(QrCodeRenderer.class);
        ExecutorService pollingExecutor = pollingExecutor();

        ILinkClient firstClient = client("qr-one");
        ILinkClient secondClient = client("qr-two");
        ILinkClient refreshedSecondClient = client("qr-two-refreshed");
        when(clientFactory.create(
                eq(config),
                nullable(ResumeContext.class),
                any(OnLoginListener.class),
                any(OnMessageListener.class)))
                .thenReturn(firstClient, secondClient, refreshedSecondClient);

        IlinkSessionRegistry sessionRegistry = new IlinkSessionRegistry(
                properties,
                config,
                clientFactory,
                dispatcher,
                qrCodeRenderer,
                pollingExecutor);
        WechatChannel channel = new IlinkWechatChannel(sessionRegistry);
        channel.start();

        try {
            WechatSession first = channel.createSession();
            WechatSession second = channel.createSession();

            assertThat(channel.listSessions())
                    .extracting(WechatSession::sessionId)
                    .containsExactlyInAnyOrder(first.sessionId(), second.sessionId());
            verify(firstClient, never()).close();

            channel.refreshLogin(second.sessionId());

            verify(secondClient).close();
            verify(firstClient, never()).close();

            channel.removeSession(first.sessionId());

            verify(firstClient).close();
            verify(refreshedSecondClient, never()).close();
            assertThat(channel.listSessions())
                    .extracting(WechatSession::sessionId)
                    .containsExactly(second.sessionId());
        } finally {
            channel.stop();
            pollingExecutor.shutdownNow();
        }
    }

    private static ExecutorService pollingExecutor() {
        return new ThreadPoolExecutor(
                0,
                4,
                30L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    private static ILinkClient client(String qrCode) {
        ILinkClient client = mock(ILinkClient.class);
        when(client.executeLogin()).thenReturn(qrCode);
        when(client.getLoginStatus()).thenReturn(new LoginStatus());
        when(client.getConnectionStatus()).thenReturn(ConnectionStatus.NOT_CONNECTED);
        when(client.isLoggedIn()).thenReturn(false);
        return client;
    }
}
