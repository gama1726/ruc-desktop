package ru.ruc.desktop.service;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ruc.desktop.domain.Machine;
import ru.ruc.desktop.domain.RemoteAccessSession;
import ru.ruc.desktop.domain.SessionStatus;
import ru.ruc.desktop.repository.MachineRepository;
import ru.ruc.desktop.repository.RemoteAccessSessionRepository;
import ru.ruc.desktop.web.dto.SessionResponse;
import ru.ruc.desktop.web.dto.StartSessionRequest;

@Service
public class SessionService {

    private final MachineRepository machineRepository;
    private final RemoteAccessSessionRepository sessionRepository;
    private final ConnectionHintBuilder hintBuilder;

    public SessionService(
            MachineRepository machineRepository,
            RemoteAccessSessionRepository sessionRepository,
            ConnectionHintBuilder hintBuilder) {
        this.machineRepository = machineRepository;
        this.sessionRepository = sessionRepository;
        this.hintBuilder = hintBuilder;
    }

    @Transactional
    public SessionResponse start(StartSessionRequest req, String operatorUserId) {
        Machine machine = machineRepository
                .findById(req.machineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "machine not found"));
        if (!machine.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "machine inactive");
        }
        RemoteAccessSession s = new RemoteAccessSession();
        s.setMachine(machine);
        s.setOperatorUserId(operatorUserId);
        s.setStatus(SessionStatus.ACTIVE);
        s.setStartedAt(Instant.now());
        s = sessionRepository.save(s);
        return toResponse(s);
    }

    @Transactional
    public SessionResponse close(long sessionId, String operatorUserId) {
        RemoteAccessSession s = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
        if (s.getStatus() == SessionStatus.CLOSED) {
            return toResponse(s);
        }
        if (!s.getOperatorUserId().equals(operatorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your session");
        }
        s.setStatus(SessionStatus.CLOSED);
        s.setEndedAt(Instant.now());
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listActiveForOperator(String operatorUserId) {
        return sessionRepository
                .findByOperatorUserIdAndStatusOrderByStartedAtDesc(operatorUserId, SessionStatus.ACTIVE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SessionResponse toResponse(RemoteAccessSession s) {
        Machine m = s.getMachine();
        return SessionResponse.from(s, hintBuilder.hint(m), hintBuilder.deepLink(m));
    }
}
