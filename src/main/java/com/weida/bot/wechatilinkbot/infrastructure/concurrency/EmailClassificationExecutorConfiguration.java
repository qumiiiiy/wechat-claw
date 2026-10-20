package com.weida.bot.wechatilinkbot.infrastructure.concurrency;

import com.weida.bot.wechatilinkbot.infrastructure.ai.EmailClassificationProperties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bounded worker pool for model-backed email action classification. */
@Configuration(proxyBeanMethods = false)
public class EmailClassificationExecutorConfiguration {

    @Bean(name = "emailClassificationExecutor", destroyMethod = "shutdownNow")
    ExecutorService emailClassificationExecutor(EmailClassificationProperties properties) {
        var queue = properties.getQueueCapacity() == 0
                ? new SynchronousQueue<Runnable>()
                : new ArrayBlockingQueue<Runnable>(properties.getQueueCapacity());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                properties.getMaxConcurrentRequests(),
                properties.getMaxConcurrentRequests(),
                30,
                TimeUnit.SECONDS,
                queue,
                new NamedThreadFactory("email-classify-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
