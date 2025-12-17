package org.example.utils.repeater;

class RepeaterFutureImpl implements RepeaterFuture {
    // Так как задача у нас показать владение базовыми synchronized/wait/notify(All),
    // то используем обычный Object lock и городим свою обработку ошибок.
    // А так бы тут хорошо смотрелся бы CompletableFuture, в нем из коробки есть блокирующий join и обработка ошибок.

    // Почему же у нас подход synchronized (lock), а не просто synchronized над методом, да потому что вдруг
    // кто-то додумается объект класса вкорячить в synchronized блок. Просто страхуемся.

    private final Object lock = new Object();

    private Throwable error;
    private boolean isFinished;

    @Override
    public void await() throws InterruptedException {
        synchronized (lock) {
            while (!isFinished) {
                lock.wait();
            }

            if (error != null) {
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }

                throw new RuntimeException(error);
            }
        }
    }

    void signalComplete() {
        synchronized (lock) {
            isFinished = true;
            lock.notifyAll();
        }
    }

    void signalError(Throwable throwable) {
        synchronized (lock) {
            // Защита от перезаписи результата
            if (isFinished) {
                return;
            }

            error = throwable;
            isFinished = true;
            lock.notifyAll();
        }
    }

    boolean hasError() {
        synchronized (lock) {
            return error != null;
        }
    }
}