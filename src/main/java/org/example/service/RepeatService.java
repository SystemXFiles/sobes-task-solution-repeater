package org.example.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class RepeatService {
    private final List<HistoryItem> historyOfRepeats = new ArrayList<>();
    private final Repeater repeater;

    public RepeatService(ApplicationConfig config) {
        repeater = new Repeater(config.getPoolSize());
    }

    @SneakyThrows
    public void repeat(RepeatRequest repeatRequest) {
        repeater.repeat(
                repeatRequest.getRepeatCount(),
                repeatRequest.getDelayInMillis(),
                (repeatNumber) -> executeTask(repeatRequest, repeatNumber)
        ).await();
    }

    public List<HistoryItem> getHistoryOfRepeats() {
        synchronized (historyOfRepeats) {
            return List.copyOf(historyOfRepeats);
        }
    }

    private void executeTask(RepeatRequest repeatRequest, long repeatNumber) {
        log.info(
                "Task executed: threadName='{}', message='{}', repeatNumber={}",
                Thread.currentThread().getName(),
                repeatRequest.getMessage(),
                repeatNumber
        );

        addToHistory(repeatRequest, repeatNumber);
    }

    private void addToHistory(RepeatRequest repeatRequest, long repeatNumber) {
        var historyItem = new HistoryItem(
                repeatRequest.getUsername(),
                repeatNumber,
                repeatRequest.getMessage()
        );

        synchronized (historyOfRepeats) {
            historyOfRepeats.add(historyItem);
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
