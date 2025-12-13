package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;

import java.util.PriorityQueue;
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
    private static final RepeaterTask EMPTY_TASK = new RepeaterTask() {
    };

    private final RepeaterTaskQueue queue = new RepeaterTaskQueue();
    private final RepeaterWorker[] workers;

    private volatile boolean isRunning = false;

    public Repeater(int workerPoolSize) {
        workers = new RepeaterWorker[workerPoolSize];
    }

    public synchronized void init() {
        if (isRunning) {
            return;
        }

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new RepeaterWorker(queue, i);
            workers[i].start();
        }

        isRunning = true;
    }

    public synchronized void shutdown() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        // Сначала все уведомляем, что пора закруглятся
        for (RepeaterWorker worker : workers) {
            worker.signalStop();
        }

        // Потом ждем завершения всех
        for (RepeaterWorker worker : workers) {
            worker.awaitStop();
        }
    }

    public RepeaterTask repeat(int repeatCount, int repeatDelayInMillis, RepeaterCallback repeaterCallback) {
        if (!isRunning) {
            throw new IllegalStateException("Repeater не инициализирован или был остановлен");
        }

        if (repeatCount < 1) {
            return EMPTY_TASK;
        }

        var repeatDelayInNanos = TimeUnit.MILLISECONDS.toNanos(repeatDelayInMillis);
        var repeaterTask = new RepeaterTaskImpl(repeatCount, repeatDelayInNanos, repeaterCallback);

        queue.addTask(repeaterTask);

        return repeaterTask;
    }
}
