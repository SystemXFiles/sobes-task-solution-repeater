package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Value
public class RepeatRequest {
    @Schema(description = "Задержка между повторами в миллисекундах")
    @NotNull
    Integer delayInMillis;

    @Schema(description = "Количество повторов сообщений")
    @NotNull
    Integer repeatCount;

    @Schema(description = "Пользователь-инициатор повтора сообщений")
    @NotBlank
    String username;

    @Schema(description = "Сообщение, которое требуется повторить")
    @NotBlank
    String message;
}
