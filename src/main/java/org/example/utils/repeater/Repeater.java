package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;

import java.util.PriorityQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Утилита для повторного выполнения задач заданное количество раз с требуемой задержкой на указанном количестве воркеров.
 * <p>
 * Плюсы:
 * <ul>
 *      <li>баланс между простотой и эффективностью (спасибо {@link ReentrantLock} и его {@link Condition}, позволившим сделать красиво код);</li>
 *      <li>лишний раз блокировки не дергает (один дежурный поток за раз и его пробуждение только когда порядок очереди изменился);</li>
 *      <li>максимально любит поспать, если работы нет (await ровно до наступления момента выполнения задачи у дежурного потока, остальные спят);</li>
 *      <li>не требует перебора задач для поиска ближайшей (за счет {@link PriorityQueue}).</li>
 * </ul>
 * <p>
 * Минусы:
 * <ul>
 *      <li>при высокой частоте выполнения задач узким местом станет общий queueLock (конкуренция);</li>
 *      <li>при большом количестве задач в очереди узким местом будет PriorityQueue (рост вычислительной сложности).</li>
 * </ul>
 */
@Slf4j
public class Repeater {
    private final RepeaterQueue queue = new RepeaterQueue();
    private volatile boolean isRunning = false;

    private final Thread[] workers;
    private final ThreadFactory threadFactory;

    public Repeater(int workerPoolSize) {
        this(workerPoolSize, new RepeaterThreadFactory());
    }

    public Repeater(int workerPoolSize, ThreadFactory threadFactory) {
        this.workers = new Thread[workerPoolSize];
        this.threadFactory = threadFactory;
    }

    public synchronized void init() {
        if (isRunning) {
            return;
        }

        for (int i = 0; i < workers.length; i++) {
            var worker = new RepeaterWorker(queue);
            var thread = threadFactory.newThread(worker);
            thread.start();
            workers[i] = thread;
        }

        isRunning = true;
    }

    public synchronized void shutdown() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        // Сначала все уведомляем, что пора закруглятся
        for (var worker : workers) {
            worker.interrupt();
        }

        // Потом ждем завершения всех
        for (var worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Нас прервали во время ожидания остановки");
            }
        }
    }

    public RepeaterFuture repeat(int repeatCount, int repeatDelayInMillis, RepeaterCallback repeaterCallback) {
        if (!isRunning) {
            throw new IllegalStateException("Repeater не инициализирован или был остановлен");
        }

        if (repeatCount < 1) {
            return RepeaterFuture.EMPTY_TASK;
        }

        var repeatDelayInNanos = TimeUnit.MILLISECONDS.toNanos(repeatDelayInMillis);
        var repeaterFuture = new RepeaterFutureImpl();
        var repeaterTask = new RepeaterQueueItem(repeatCount, repeatDelayInNanos, repeaterCallback, repeaterFuture);

        queue.addTask(repeaterTask);

        return repeaterFuture;
    }
}

