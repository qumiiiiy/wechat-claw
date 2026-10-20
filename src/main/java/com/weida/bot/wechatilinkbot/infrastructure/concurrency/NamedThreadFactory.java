package com.weida.bot.wechatilinkbot.infrastructure.concurrency;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates platform threads with a stable, diagnosable name prefix.
 *
 * @author ryx
 * @since 2026-07-20
 */
public final class NamedThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public NamedThreadFactory(String namePrefix) {
        if (namePrefix == null || namePrefix.isBlank()) {
            throw new IllegalArgumentException("namePrefix 不能为空");
        }
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, namePrefix + sequence.getAndIncrement());
        thread.setDaemon(false);
        return thread;
    }
}
