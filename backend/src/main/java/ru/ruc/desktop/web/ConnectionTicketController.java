package ru.ruc.desktop.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ruc.desktop.service.ConnectionTicketService;
import ru.ruc.desktop.web.dto.ConnectionTicketResponse;
import ru.ruc.desktop.web.dto.IssueConnectionTicketRequest;

@RestController
@RequestMapping("/api/tickets")
@Validated
public class ConnectionTicketController {

    private final ConnectionTicketService ticketService;

    public ConnectionTicketController(ConnectionTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ConnectionTicketResponse issue(
            @Valid @RequestBody IssueConnectionTicketRequest body,
            @RequestHeader(value = SessionController.USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return ticketService.issue(operatorUserId, body);
    }

    @GetMapping("/issued")
    public List<ConnectionTicketResponse> listIssued(
            @RequestHeader(value = SessionController.USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return ticketService.listIssued(operatorUserId);
    }

    @GetMapping("/pull")
    public ResponseEntity<ConnectionTicketResponse> pullForAgent(@RequestParam String remoteId) {
        return ticketService
                .pullForAgent(remoteId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PatchMapping("/{token}/consume")
    public ConnectionTicketResponse consume(
            @PathVariable String token,
            @RequestHeader(value = SessionController.USER_HEADER, defaultValue = "demo") String operatorUserId) {
        return ticketService.consume(token, operatorUserId);
    }
}
