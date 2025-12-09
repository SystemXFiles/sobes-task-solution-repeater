package org.example.service;

import lombok.Data;
import lombok.SneakyThrows;
import org.example.config.ApplicationConfig;
import org.example.model.HistoryItem;
import org.example.model.RepeatRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class RepeatService {
    @Data
    private final static class QueueItem {
        private final String username;
        private final String message;
        private final Long startAfterTime;
        private final Long repeatNumber;
        private final CountDownLatch countDownLatch;
    }

    private final List<Thread> threads;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition queueAddedItems = lock.newCondition();

    private final Queue<QueueItem> queue
            = new PriorityQueue<>(Comparator.comparingLong(queueItem -> queueItem.startAfterTime));

    private final List<HistoryItem> historyItems = new ArrayList<>();

    public RepeatService(ApplicationConfig config) {
        final List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < config.getPoolSize(); i++) {
            Thread worker = new Thread(this::handleQueue);
            threads.add(worker);

            worker.start();
        }

        this.threads = threads;
    }

    @SneakyThrows
    public void repeat(RepeatRequest repeatRequest) {
        if (repeatRequest.getRepeatCount() < 1) {
            return;
        }

        long now = System.currentTimeMillis();

        lock.lock();

        CountDownLatch countDownLatch = new CountDownLatch(repeatRequest.getRepeatCount());

        try {
            for (long i = 0; i < repeatRequest.getRepeatCount(); i++) {
                long startAfterTime = now + i * repeatRequest.getDelayInMillis();

                queue.add(new QueueItem(
                        repeatRequest.getUsername(),
                        repeatRequest.getMessage(),
                        startAfterTime,
                        i,
                        countDownLatch
                ));
            }

            queueAddedItems.signal();
        } finally {
            lock.unlock();
        }

        countDownLatch.await();
    }

    public List<HistoryItem> getHistoryOfRequests() {
        synchronized (historyItems) {
            return List.copyOf(historyItems);
        }
    }

    @SneakyThrows
    private void handleQueue() {
        while (!Thread.interrupted()) {
            QueueItem queueItem;

            lock.lock();
            try {
                do {
                    queueItem = queue.peek();

                    if (queueItem == null) {
                        queueAddedItems.await();
                    } else {
                        long now = System.currentTimeMillis();
                        long delay = Math.max(0L, queueItem.startAfterTime - now);

                        //noinspection ResultOfMethodCallIgnored
                        queueAddedItems.await(delay, TimeUnit.MILLISECONDS);

                        now = System.currentTimeMillis();

                        if (queueItem == queue.peek() && now > queueItem.startAfterTime) {
                            queue.remove();
                            executeTask(queueItem);
                        }
                    }
                } while (queueItem == null);
            } finally {
                lock.unlock();
            }
        }
    }

    private void executeTask(QueueItem queueItem) {
//        System.out.println( queueItem.getMessage());
        System.out.printf("%s %s %d%n", Thread.currentThread().getName(), queueItem.getMessage(), queueItem.getRepeatNumber()); // Debug

        synchronized (historyItems) {
            historyItems.add(new HistoryItem(
                    queueItem.getUsername(),
                    queueItem.getRepeatNumber(),
                    queueItem.getMessage()
            ));
        }

        queueItem.countDownLatch.countDown();
    }
}
