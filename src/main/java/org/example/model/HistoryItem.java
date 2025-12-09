package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class HistoryItem {
    @Schema(description = "Пользователь-инициатор повтора сообщений")
    private final String username;

    @Schema(description = "Порядковый номер повторения")
    private final long repeatNumber;

    @Schema(description = "Сообщение, которое требуется повторить")
    private final String message;
}
