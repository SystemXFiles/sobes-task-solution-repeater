package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.model.HistoryItem;
import org.example.model.RepeatRequest;
import org.example.service.RepeatService;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController()
@RequestMapping("repeat")
@Tag(name = "Repeat", description = "Сервис для периодичного повтора отсылки сообщений")
@RequiredArgsConstructor
public class RepeatController {
    private final RepeatService repeatService;

    @PostMapping
    @Operation(description = "Добавляет в очередь задачу для отправки сообщения по заданным в теле параметрам")
    public void doRepeat(@Valid @RequestBody RepeatRequest repeatRequest) {
        repeatService.repeat(repeatRequest);
        // Spring по умолчанию возвращает статус 200 для методов void
        // Потому и не надо намеренно проставлять статус 200
    }

    @GetMapping
    @Operation(description = "Получает историю выполнения отправок сообщений")
    public List<HistoryItem> getHistory() {
        return repeatService.getHistoryOfRepeats();
    }
}
