package org.example.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
public class RepeatRequest {
    @Schema(description = "Задержка между повторами в миллисекундах")
    @NotNull
    private Integer delayInMillis;

    @Schema(description = "Количество повторов сообщений")
    @NotNull
    private Integer repeatCount;

    @Schema(description = "Пользователь-инициатор повтора сообщений")
    @NotBlank
    private String username;

    @Schema(description = "Сообщение, которое требуется повторить")
    @NotBlank
    private String message;
}
