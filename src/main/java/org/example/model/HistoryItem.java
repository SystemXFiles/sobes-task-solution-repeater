package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

@Value
public class HistoryItem {
    @Schema(description = "Пользователь-инициатор повтора сообщений")
    String username;

    @Schema(description = "Порядковый номер повторения")
    long repeatNumber;

    @Schema(description = "Сообщение, которое требуется повторить")
    String message;
}
