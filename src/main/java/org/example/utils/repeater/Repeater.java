package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class Repeater {
    private static final String WORKER_NAME_PREFIX = "Repeater-Worker-";
    private static final RepeaterTask EMPTY_TASK = new RepeaterTask() {
    };

    // Специально задействовал ReentrantLock fair = true, чтобы справедливо нагрузка раскидывалась по потокам,
    // Ибо не хочется городить свою справедливость на базе synchronized/wait/notify(All), лень =)

    private final ReentrantLock queueLock = new ReentrantLock(true);
    private final Condition queueChanged = queueLock.newCondition();

    private final PriorityQueue<RepeaterTaskImpl> priorityQueue = new PriorityQueue<>();

    private final List<Thread> workers = new ArrayList<>();
    private final int workerPoolSize;

    public Repeater(int workerPoolSize) {
        this.workerPoolSize = workerPoolSize;
    }

    public synchronized void init() {
        if (!workers.isEmpty()) {
            return;
        }

        for (int i = 0; i < workerPoolSize; i++) {
            var worker = new Thread(this::handleQueue, WORKER_NAME_PREFIX + i);

            worker.setDaemon(true);
            worker.start();

            workers.add(worker);
        }
    }

    public synchronized void shutdown() {
        workers.forEach(Thread::interrupt);
        workers.clear();
    }

    public RepeaterTask repeat(int repeatCount, int repeatDelayInMillis, RepeaterCallback repeaterCallback) {
        if (repeatCount < 1) {
            return EMPTY_TASK;
        }

        var repeatDelayInNanos = TimeUnit.MILLISECONDS.toNanos(repeatDelayInMillis);
        var repeaterTask = new RepeaterTaskImpl(repeatCount, repeatDelayInNanos, repeaterCallback);

        addToQueueAndSignalAboutChanges(repeaterTask);

        return repeaterTask;
    }

    private void handleQueue() {
        while (!Thread.interrupted()) {
            var jobToRun = waitNext();

            // null возвращается из waitNext только в случае если был прерван поток, значит цикл завершаем
            if (jobToRun == null) break;

            jobToRun.execute();

            if (jobToRun.scheduleNextLaunchIfHasNext()) {
                addToQueueAndSignalAboutChanges(jobToRun);
            } else {
                jobToRun.signalComplete();
            }
        }
    }

    @Nullable
    private RepeaterTaskImpl waitNext() {
        RepeaterTaskImpl jobToRun = null;

        queueLock.lock();
        try {
            while (jobToRun == null) {
                RepeaterTaskImpl head = priorityQueue.peek();

                if (head == null) {
                    queueChanged.await();
                } else {
                    long timeUntilNextRun = head.timeUntilNextRun();

                    if (timeUntilNextRun <= 0) {
                        jobToRun = priorityQueue.poll();
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        queueChanged.awaitNanos(timeUntilNextRun);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            queueLock.unlock();
        }

        return jobToRun;
    }

    private void addToQueueAndSignalAboutChanges(RepeaterTaskImpl queueItem) {
        queueLock.lock();
        try {
            priorityQueue.add(queueItem);
            queueChanged.signalAll();
        } finally {
            queueLock.unlock();
        }
    }
}
