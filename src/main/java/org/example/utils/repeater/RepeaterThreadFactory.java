package org.example.utils.repeater;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class RepeaterThreadFactory implements ThreadFactory {
    private static final String WORKER_NAME_PREFIX = "Repeater-Worker-";
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Thread newThread(Runnable runnable) {
        var name = WORKER_NAME_PREFIX + counter.getAndIncrement();
        var thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }
}
