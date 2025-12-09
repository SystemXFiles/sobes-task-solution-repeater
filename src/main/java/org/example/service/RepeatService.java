package org.example.service;

import lombok.SneakyThrows;
import org.example.config.ApplicationConfig;
import org.example.model.HistoryItem;
import org.example.model.RepeatRequest;
import org.example.utils.repeater.Repeater;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Service
public class RepeatService {
    private final Repeater repeater;
    private final List<HistoryItem> historyOfRepeats = new ArrayList<>();

    public RepeatService(ApplicationConfig config) {
        repeater = new Repeater(config.getPoolSize());
    }

    @SneakyThrows
    public void repeat(RepeatRequest repeatRequest) {
        repeater.repeat(
                repeatRequest.getRepeatCount(),
                repeatRequest.getDelayInMillis(),
                (repeatNumber) -> {
                    System.out.printf(
                            "%s %s %d%n",
                            Thread.currentThread().getName(),
                            repeatRequest.getMessage(),
                            repeatNumber
                    );

                    addToHistory(repeatRequest, repeatNumber);
                }
        ).await();
    }

    private void addToHistory(RepeatRequest repeatRequest, long repeatNumber) {
        HistoryItem historyItem = new HistoryItem(
                repeatRequest.getUsername(),
                repeatNumber,
                repeatRequest.getMessage()
        );

        synchronized (historyOfRepeats) {
            historyOfRepeats.add(historyItem);
        }
    }

    public List<HistoryItem> getHistoryOfRepeats() {
        synchronized (historyOfRepeats) {
            return List.copyOf(historyOfRepeats);
        }
    }

    @PostConstruct
    private void init() {
        repeater.init();
    }

    @PreDestroy
    private void destroy() {
        repeater.shutdown();
    }
}
