package org.example.utils.repeater;

import lombok.RequiredArgsConstructor;
import org.example.utils.TaskFuture;
import org.example.utils.TimeUtils;

@RequiredArgsConstructor
class RepeaterTaskImpl implements RepeaterTask, Comparable<RepeaterTaskImpl> {
    private final int repeatCount;
    private final long repeatDelayInNanos;
    private final RepeaterCallback repeaterCallback;

    // По идее volatile не нужен, ибо у нас барьеры памяти есть благодаря общему lock, но на всякий случай добавил
    // для подстраховки.
    //
    // Допустим кто-то в будущем код решил доработать и не знает особенности работы текущей реализации.

    private volatile int repeatNumber = 0;
    private long startAfterTime = TimeUtils.nowNanoTime();

    private final TaskFuture taskFuture = new TaskFuture();

    @Override
    public void await() throws InterruptedException {
        taskFuture.await();
    }

    @Override
    public int compareTo(RepeaterTaskImpl other) {
        return Long.compare(this.startAfterTime, other.startAfterTime);
    }

    void tryExecute() {
        try {
            // Инкрементация допустима, ибо у нас владеть текущей задачей может только один поток,
            // соответственно инкремент нормально отработает несмотря на свою не-атомарность

            //noinspection NonAtomicOperationOnVolatileField
            repeaterCallback.callback(repeatNumber++);
        } catch (Throwable throwable) {
            // Проверяет, не поймали ли мы случайно исключение прерывания, если да, то восстанавливает флаг, дабы он
            // дальше корректно был обработан
            checkInterrupt(throwable);

            signalError(throwable);
        }
    }

    boolean hasError() {
        return taskFuture.hasError();
    }

    void signalComplete() {
        taskFuture.signalComplete();
    }

    long timeUntilNextRun() {
        return startAfterTime - TimeUtils.nowNanoTime();
    }

    void calculateAndSetNextLaunchTime() {
        var nextLaunchTime = startAfterTime + repeatDelayInNanos;
        var now = TimeUtils.nowNanoTime();

        // Данный max решает две проблемы:
        // 1. Если кто-то додумался repeatDelayInMillis бахнуть 0, то у нас startAfterTime не будет вообще меняться
        //    и позиция в очереди соответственно тоже. Как итог задача наша будет каждый раз браться заново, что не
        //    справедливо по отношению к тем, кто дальше в очереди. Потому мы сдвигаем задачу, которая "сразу" должна
        //    быть выполнена на текущее время. Это дает возможность выполнится другим, время которых уже пришло (в прошлом уже).
        //
        // 2. Защищает от лавины. Если вдруг сервис завис на долгое время, то могут накопиться задачи на
        //    выполнение. При пробуждении сервиса он захочет все и сразу выполнить задачи, время которых наступило.
        //    max же предотвращает это по сути запрещая планирование задним числом.
        //    Правда это касается именно повторений, если будет 100 задач отдельных (именно в очереди отдельные объекты),
        //    они все равно все независимо бахнут, это надо учитывать.

        startAfterTime = Math.max(nextLaunchTime, now);
    }

    boolean hasNext() {
        return repeatNumber < repeatCount;
    }

    private void signalError(Throwable throwable) {
        taskFuture.signalError(throwable);
    }

    private static void checkInterrupt(Throwable throwable) {
        boolean isInterruptedException = throwable instanceof InterruptedException;
        boolean causeIsInterruptedException = throwable.getCause() instanceof InterruptedException;

        if (!isInterruptedException && !causeIsInterruptedException) {
            return;
        }

        Thread.currentThread().interrupt();
    }
}
