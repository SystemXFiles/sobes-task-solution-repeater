package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class Repeater {
    private final static RepeaterTask EMPTY_TASK = new RepeaterTask() {
    };

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition queueChanged = lock.newCondition();

    private final Queue<RepeaterTaskImpl> queue = new PriorityQueue<>();

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
            Thread worker = new Thread(this::handleQueue, "Repeater-Worker-" + i);
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

        RepeaterTaskImpl repeaterTask = new RepeaterTaskImpl(repeatCount, repeatDelayInMillis, repeaterCallback);
        addToQueueAndSignalAboutChanges(repeaterTask);

        return repeaterTask;
    }

    private void handleQueue() {
        while (!Thread.interrupted()) {
            RepeaterTaskImpl jobToRun = waitNext();

            // null возвращается из waitNext только в случае если был прерван поток, значит цикл завершаем
            if (jobToRun == null) break;

            jobToRun.execute();

            if (jobToRun.scheduleNextLaunchIfHasNext()) {
                addToQueueAndSignalAboutChanges(jobToRun);
            } else {
                jobToRun.signalTaskIsFree();
            }
        }
    }

    @Nullable
    private RepeaterTaskImpl waitNext() {
        RepeaterTaskImpl jobToRun = null;

        lock.lock();
        try {
            while (jobToRun == null) {
                RepeaterTaskImpl head = queue.peek();

                if (head == null) {
                    queueChanged.await();
                } else {
                    long timeUntilNextRun = head.timeUntilNextRun();

                    if (timeUntilNextRun <= 0) {
                        jobToRun = queue.poll();
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        queueChanged.await(timeUntilNextRun, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        return jobToRun;
    }

    private void addToQueueAndSignalAboutChanges(RepeaterTaskImpl queueItem) {
        lock.lock();
        try {
            queue.add(queueItem);
            queueChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
