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

    private final ReentrantLock queueLock = new ReentrantLock();

    // Два Condition нужны, чтобы не всем просыпаться по таймеру, а одному, а он уже, если надо, пробудит других
    private final Condition timerWait = queueLock.newCondition();
    private final Condition idleWait = queueLock.newCondition();

    private final PriorityQueue<RepeaterTaskImpl> priorityQueue = new PriorityQueue<>();

    private final List<Thread> workers = new ArrayList<>();
    private final int workerPoolSize;

    // Поток, который дежурит, ибо нет смысла всем потокам сидеть на таймере
    private Thread timerWaiter = null;

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
            try {
                var jobToRun = waitNext();

                // null возвращается из waitNext только в случае если был прерван поток, значит цикл завершаем
                if (jobToRun == null) break;

                try {
                    jobToRun.execute();
                } catch (Throwable e) {
                    jobToRun.signalError(e);
                }

                if (jobToRun.scheduleNextLaunchIfHasNext()) {
                    addToQueueAndSignalAboutChanges(jobToRun);
                } else {
                    jobToRun.signalComplete();
                }
            } catch (Throwable throwable) {
                log.error("Ошибка в рабочем цикле...", throwable);
            }
        }
    }

    @Nullable
    private RepeaterTaskImpl waitNext() {
        var currentThread = Thread.currentThread();

        queueLock.lock();
        try {
            while (true) {
                var head = priorityQueue.peek();

                // Если очередь пуста, то дежурный не нужен и мы отправляемся спать
                if (head == null) {
                    clearTimerWaiter();
                    idleWait.await();
                    continue;
                }

                long timeUntilNextRun = head.timeUntilNextRun();

                // Если время задачи уже пришло, забираем ее себе
                if (timeUntilNextRun <= 0) {
                    var jobToRun = priorityQueue.poll();
                    clearTimerWaiter();

                    // Будим кого-нибудь еще, чтобы он стал новым дежурным, ибо сами мы будем сейчас задачу выполнять
                    idleWait.signal();
                    return jobToRun;
                }


                // Если дежурный уже есть и это не мы, то отдыхаем пока не пнут нас
                if (timerWaiter != null && timerWaiter != currentThread) {
                    idleWait.await();
                    continue;
                }

                // Становимся дежурным (тут два варианта либо это уже мы либо его еще нет)
                timerWaiter = currentThread;

                // Ждем до момента выполнения задачи
                //noinspection ResultOfMethodCallIgnored
                timerWait.awaitNanos(timeUntilNextRun);
            }
        } catch (InterruptedException e) {
            clearTimerWaiterAndWakeupOneIdle();
            // Возвращаем флаг для кого-нибудь снаружи на всяк случай
            Thread.currentThread().interrupt();
            return null;
        } finally {
            queueLock.unlock();
        }
    }

    private void clearTimerWaiterAndWakeupOneIdle() {
        if (timerWaiter != Thread.currentThread()) {
            return;
        }

        // Словили остановку? Ну теперь мы не дежурный точно
        timerWaiter = null;
        // Пытаемся разбудить замену
        idleWait.signal();
    }

    private void clearTimerWaiter() {
        if (timerWaiter == Thread.currentThread()) {
            timerWaiter = null;
        }
    }

    private void addToQueueAndSignalAboutChanges(RepeaterTaskImpl queueItem) {
        queueLock.lock();
        try {
            priorityQueue.add(queueItem);

            // Если задача встала в начало, надо разбудить поток для перерасчета таймера
            if (priorityQueue.peek() == queueItem) {
                // Если дежурного нет, будем кого угодно
                if (timerWaiter == null) {
                    // Но только одного, зачем нам очередь перед блокировкой?
                    idleWait.signal();
                } else {
                    // Если дежурный есть, будим его (он спит на таймере, надо пересчитать)
                    timerWait.signal();
                }
            }
        } finally {
            queueLock.unlock();
        }
    }
}
