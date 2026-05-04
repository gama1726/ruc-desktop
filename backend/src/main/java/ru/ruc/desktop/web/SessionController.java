package ru.ruc.desktop.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.ruc.desktop.service.ConnectionHintBuilder;
import ru.ruc.desktop.service.SessionAuditService;
import ru.ruc.desktop.service.SessionService;
import ru.ruc.desktop.web.dto.QuickConnectRequest;
import ru.ruc.desktop.web.dto.QuickConnectResponse;
import ru.ruc.desktop.web.dto.SessionResponse;
import ru.ruc.desktop.web.dto.StartSessionRequest;

@RestController
@RequestMapping("/api/sessions")
@Validated
public class SessionController {

    public static final String USER_HEADER = "X-Ruc-User";

    private final SessionService sessionService;
    private final ConnectionHintBuilder connectionHintBuilder;
    private final SessionAuditService auditService;

    public SessionController(
            SessionService sessionService,
            ConnectionHintBuilder connectionHintBuilder,
            SessionAuditService auditService) {
        this.sessionService = sessionService;
        this.connectionHintBuilder = connectionHintBuilder;
        this.auditService = auditService;
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

    @PostMapping("/quick-connect")
    public QuickConnectResponse quickConnect(
            @Valid @RequestBody QuickConnectRequest body,
            @RequestHeader(value = USER_HEADER, defaultValue = "demo") String operatorUserId) {
        String normalizedRemoteId = body.remoteId().replaceAll("\\s+", "").trim();
        if (normalizedRemoteId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteId is blank");
        }
        String deepLink = connectionHintBuilder.deepLinkFromPeer(normalizedRemoteId);
        auditService.log(operatorUserId, null, normalizedRemoteId, "QUICK_CONNECT", "source=manual-id");
        return new QuickConnectResponse(
                normalizedRemoteId, connectionHintBuilder.quickConnectHint(), deepLink);
    }

    @PatchMapping("/{id}/close")
    public SessionResponse close(
            @PathVariable long id,
            @RequestHeader(value = USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return sessionService.close(id, operatorUserId);
    }
}
