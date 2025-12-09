package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NonNull;

import javax.validation.constraints.NotBlank;

@Data
public class RepeatRequest {
    @Schema(description = "Задержка между повторами в миллисекундах")
    private final int delayInMillis;

    @Schema(description = "Количество повторов сообщений")
    private final int repeatCount;

    @Schema(description = "Пользователь-инициатор повтора сообщений")
    @NotBlank
    @NonNull
    private final String username;

    @Schema(description = "Сообщение, которое требуется повторить")
    @NotBlank
    @NonNull
    private final String message;
}
