package org.example.utils.repeater;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class RepeaterWorker implements Runnable {
    private final RepeaterQueue queue;

    RepeaterWorker(RepeaterQueue queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                // Извлекаем следующую задачу из очереди (при наличии, иначе ждем)
                var jobToRun = queue.awaitNextTask();

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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable throwable) {
                log.error("Ошибка в рабочем цикле...", throwable);
            }
        }
    }
}
