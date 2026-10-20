package com.weida.bot.wechatilinkbot.channel.ilink;

import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.weida.bot.wechatilinkbot.infrastructure.concurrency.NamedThreadFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the WeChat SDK and its bounded worker pools.
 *
 * @author ryx
 * @since 2026-07-20
 */
@Configuration(proxyBeanMethods = false)
public class IlinkConfiguration {

    @Bean
    ILinkConfig iLinkConfig(IlinkProperties properties) {
        return ILinkConfig.builder()
                .connectTimeoutMs(properties.getConnectTimeout().toMillis())
                .readTimeoutMs(properties.getReadTimeout().toMillis())
                .writeTimeoutMs(properties.getWriteTimeout().toMillis())
                .loginTimeoutMs(properties.getLoginTimeout().toMillis())
                .heartbeatEnabled(false)
                .channelVersion(properties.getChannelVersion())
                .build();
    }

    @Bean(name = "wechatMessagePollingExecutor", destroyMethod = "shutdownNow")
    ExecutorService wechatMessagePollingExecutor(IlinkProperties properties) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0,
                properties.getMaxBotSessions(),
                properties.getWorkerKeepAlive().toMillis(),
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new NamedThreadFactory("wechat-poll-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "wechatMessageHandlingExecutor", destroyMethod = "shutdownNow")
    ExecutorService wechatMessageHandlingExecutor(IlinkProperties properties) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                properties.getMessageHandlingCoreThreads(),
                properties.getMessageHandlingMaxThreads(),
                properties.getWorkerKeepAlive().toMillis(),
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getMessageHandlingQueueCapacity()),
                new NamedThreadFactory("wechat-message-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "wechatMessageRetryScheduler", destroyMethod = "shutdownNow")
    ScheduledExecutorService wechatMessageRetryScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
                1,
                new NamedThreadFactory("wechat-retry-"),
                new ThreadPoolExecutor.AbortPolicy());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }
}
