package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class RepeaterWorker implements Runnable {
    private static final String WORKER_NAME_PREFIX = "Repeater-Worker-";
    private final RepeaterTaskQueue queue;
    private final Thread thread;

    RepeaterWorker(RepeaterTaskQueue queue, int workerId) {
        this.queue = queue;
        this.thread = new Thread(this, WORKER_NAME_PREFIX + workerId);
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    void signalStop() {
        thread.interrupt();
    }

    void awaitStop() {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Нас прервали во время ожидания остановки");
        }
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                // Извлекаем следующую задачу из очереди (при наличии, иначе ждем)
                var jobToRun = queue.awaitNextTask();

                // null возвращается из waitNext только в случае если был прерван поток, значит цикл завершаем
                if (jobToRun == null) break;

                // Пытаемся выполнить задачу
                jobToRun.tryExecute();

                // Если при выполнении словили ошибку, то дальше задачу не планируем
                if (jobToRun.hasError()) {
                    continue;
                }

                // Если есть еще повторы задачи, то планируем время следующего запуска и возвращаем задачу в очередь
                if (jobToRun.hasNext()) {
                    jobToRun.calculateAndSetNextLaunchTime();
                    queue.addTask(jobToRun);
                } else {
                    // Иначе отмечаем задачу как выполненную
                    jobToRun.signalComplete();
                }
            } catch (Throwable throwable) {
                log.error("Ошибка в рабочем цикле...", throwable);
            }
        }
    }
}
