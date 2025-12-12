package org.example.utils.repeater;

import org.springframework.lang.Nullable;

import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class RepeaterTaskQueue {
    // ReentrantLock используется благодаря прекрасному Condition,
    // который позволяет будить только того кого надо, а не всех подряд или случайно
    private final ReentrantLock queueLock = new ReentrantLock();

    // Два Condition нужны, чтобы не всем просыпаться по таймеру, а одному, а он уже, если надо, пробудит других
    private final Condition timerWait = queueLock.newCondition();
    private final Condition idleWait = queueLock.newCondition();

    private final PriorityQueue<RepeaterTaskImpl> priorityQueue = new PriorityQueue<>();

    // Поток, который дежурит, ибо нет смысла всем потокам сидеть на таймере
    private Thread timerWaiter = null;

    void addTask(RepeaterTaskImpl repeaterTask) {
        queueLock.lock();
        try {
            priorityQueue.add(repeaterTask);

            // Если задача встала в начало, надо разбудить поток для перерасчета таймера
            if (priorityQueue.peek() == repeaterTask) {
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

    @Nullable
    RepeaterTaskImpl awaitNextTask() {
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
}
