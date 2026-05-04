package ru.ruc.desktop.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ruc.desktop.service.SessionService;
import ru.ruc.desktop.web.dto.SessionResponse;
import ru.ruc.desktop.web.dto.StartSessionRequest;

@RestController
@RequestMapping("/api/sessions")
@Validated
public class SessionController {

    public static final String USER_HEADER = "X-Ruc-User";

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/active")
    public List<SessionResponse> listActive(
            @RequestHeader(value = USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return sessionService.listActiveForOperator(operatorUserId);
    }

    @PostMapping
    public SessionResponse start(
            @Valid @RequestBody StartSessionRequest body,
            @RequestHeader(value = USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return sessionService.start(body, operatorUserId);
    }

    @PatchMapping("/{id}/close")
    public SessionResponse close(
            @PathVariable long id,
            @RequestHeader(value = USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return sessionService.close(id, operatorUserId);
    }
}
