package ru.ruc.desktop.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ruc.desktop.domain.ConnectionTicket;
import ru.ruc.desktop.domain.ConnectionTicketStatus;
import ru.ruc.desktop.repository.ConnectionTicketRepository;
import ru.ruc.desktop.repository.MachineRepository;
import ru.ruc.desktop.web.dto.ConnectionTicketResponse;
import ru.ruc.desktop.web.dto.IssueConnectionTicketRequest;

@Service
public class ConnectionTicketService {

    private static final int DEFAULT_TTL_SECONDS = 300;
    private final SecureRandom random = new SecureRandom();

    private final ConnectionTicketRepository ticketRepository;
    private final MachineRepository machineRepository;
    private final ConnectionHintBuilder hintBuilder;
    private final SessionAuditService auditService;

    public ConnectionTicketService(
            ConnectionTicketRepository ticketRepository,
            MachineRepository machineRepository,
            ConnectionHintBuilder hintBuilder,
            SessionAuditService auditService) {
        this.ticketRepository = ticketRepository;
        this.machineRepository = machineRepository;
        this.hintBuilder = hintBuilder;
        this.auditService = auditService;
    }

    @Transactional
    public ConnectionTicketResponse issue(String operatorUserId, IssueConnectionTicketRequest req) {
        expireOverdue();

        Long machineId = req.machineId();
        String remoteId = normalize(req.remoteId());

        if (machineId != null) {
            var machine = machineRepository
                    .findById(machineId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "machine not found"));
            if (!machine.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "machine inactive");
            }
            remoteId = normalize(machine.getEnginePeerId());
            if (remoteId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "machine does not have peer id");
            }
        }

        if (remoteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "remoteId or machineId required");
        }

        int ttlSeconds = req.ttlSeconds() != null ? req.ttlSeconds() : DEFAULT_TTL_SECONDS;
        Instant now = Instant.now();

        ConnectionTicket t = new ConnectionTicket();
        t.setToken(newToken());
        t.setOperatorUserId(operatorUserId);
        t.setMachineId(machineId);
        t.setRemoteId(remoteId);
        t.setCreatedAt(now);
        t.setExpiresAt(now.plusSeconds(ttlSeconds));
        t.setStatus(ConnectionTicketStatus.ISSUED);

        t = ticketRepository.save(t);
        auditService.log(operatorUserId, machineId, remoteId, "TICKET_ISSUED", "ttlSeconds=" + ttlSeconds);

        String deepLink = hintBuilder.deepLinkFromPeer(remoteId);
        return new ConnectionTicketResponse(
                t.getToken(),
                t.getMachineId(),
                t.getRemoteId(),
                t.getExpiresAt(),
                t.getStatus().name(),
                deepLink,
                hintBuilder.quickConnectHint());
    }

    @Transactional(readOnly = true)
    public List<ConnectionTicketResponse> listIssued(String operatorUserId) {
        return ticketRepository
                .findByOperatorUserIdAndStatusOrderByCreatedAtDesc(operatorUserId, ConnectionTicketStatus.ISSUED)
                .stream()
                .map(t -> new ConnectionTicketResponse(
                        t.getToken(),
                        t.getMachineId(),
                        t.getRemoteId(),
                        t.getExpiresAt(),
                        t.getStatus().name(),
                        hintBuilder.deepLinkFromPeer(t.getRemoteId()),
                        hintBuilder.quickConnectHint()))
                .toList();
    }

    @Transactional
    public Optional<ConnectionTicketResponse> pullForAgent(String remoteId) {
        String normalizedRemoteId = normalize(remoteId);
        if (normalizedRemoteId == null) {
            return Optional.empty();
        }
        expireOverdue();
        return ticketRepository
                .findTopByRemoteIdAndStatusOrderByCreatedAtDesc(normalizedRemoteId, ConnectionTicketStatus.ISSUED)
                .map(t -> new ConnectionTicketResponse(
                        t.getToken(),
                        t.getMachineId(),
                        t.getRemoteId(),
                        t.getExpiresAt(),
                        t.getStatus().name(),
                        hintBuilder.deepLinkFromPeer(t.getRemoteId()),
                        hintBuilder.quickConnectHint()));
    }

    @Transactional
    public ConnectionTicketResponse consume(String token, String operatorUserId) {
        expireOverdue();
        var t = ticketRepository
                .findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket not found"));

        if (t.getStatus() != ConnectionTicketStatus.ISSUED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticket is not active");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            t.setStatus(ConnectionTicketStatus.EXPIRED);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ticket expired");
        }

        t.setStatus(ConnectionTicketStatus.CONSUMED);
        t.setConsumedAt(Instant.now());
        auditService.log(operatorUserId, t.getMachineId(), t.getRemoteId(), "TICKET_CONSUMED", t.getToken());

        return new ConnectionTicketResponse(
                t.getToken(),
                t.getMachineId(),
                t.getRemoteId(),
                t.getExpiresAt(),
                t.getStatus().name(),
                hintBuilder.deepLinkFromPeer(t.getRemoteId()),
                hintBuilder.quickConnectHint());
    }

    private void expireOverdue() {
        var overdue = ticketRepository.findByStatusAndExpiresAtBefore(ConnectionTicketStatus.ISSUED, Instant.now());
        for (ConnectionTicket t : overdue) {
            t.setStatus(ConnectionTicketStatus.EXPIRED);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replaceAll("\\s+", "").trim();
        return v.isBlank() ? null : v;
    }

    private String newToken() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
