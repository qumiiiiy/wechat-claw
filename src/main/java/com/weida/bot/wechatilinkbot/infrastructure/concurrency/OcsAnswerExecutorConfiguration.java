package com.weida.bot.wechatilinkbot.infrastructure.concurrency;

import com.weida.bot.wechatilinkbot.agent.OcsAnswerProperties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bounded worker pool for OCS online model requests. */
@Configuration(proxyBeanMethods = false)
public class OcsAnswerExecutorConfiguration {

    @Bean(name = "ocsAnswerExecutor", destroyMethod = "shutdownNow")
    ExecutorService ocsAnswerExecutor(OcsAnswerProperties properties) {
        var queue = properties.getQueueCapacity() == 0
                ? new SynchronousQueue<Runnable>()
                : new ArrayBlockingQueue<Runnable>(properties.getQueueCapacity());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                properties.getMaxConcurrentAnswers(),
                properties.getMaxConcurrentAnswers(),
                30,
                TimeUnit.SECONDS,
                queue,
                new NamedThreadFactory("ocs-answer-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
