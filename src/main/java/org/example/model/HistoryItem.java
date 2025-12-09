package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
public class HistoryItem {
    @Schema(description = "Пользователь-инициатор повтора сообщений")
    private String username;

    @Schema(description = "Порядковый номер повторения")
    private Long repeatNumber;

    @Schema(description = "Сообщение, которое требуется повторить")
    private String message;
}
