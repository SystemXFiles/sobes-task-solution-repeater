package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.model.HistoryItem;
import org.example.model.RepeatRequest;
import org.example.service.RepeatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

@RestController()
@RequestMapping("repeat")
@Tag(name = "Repeat", description = "Сервис для периодичного повтора отсылки сообщений")
public class RepeatController {
    @Autowired
    private RepeatService repeatService;

    @PostMapping
    @Operation(description = "Добавляет в очередь задачу для отправки сообщения по заданным в теле параметрам")
    public void doRepeat(
            HttpServletResponse response,
            @Valid @RequestBody RepeatRequest repeatRequest
    ) {
        repeatService.repeat(repeatRequest);
        response.setStatus(HttpStatus.OK.value());
    }

    @GetMapping
    @Operation(description = "Получает историю выполнения отправок сообщений")
    public List<HistoryItem> getHistory() {
        return repeatService.getHistoryOfRequests();
    }
}
