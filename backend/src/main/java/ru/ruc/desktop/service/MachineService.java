package ru.ruc.desktop.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.ruc.desktop.repository.MachineRepository;
import ru.ruc.desktop.web.dto.MachineResponse;

@Service
public class MachineService {

    private final MachineRepository machineRepository;

    public MachineService(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Transactional(readOnly = true)
    public List<MachineResponse> listActive() {
        return machineRepository.findByActiveTrueOrderByRoomCodeAscHostnameAsc().stream()
                .map(MachineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MachineResponse get(long id) {
        return machineRepository
                .findById(id)
                .map(MachineResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "machine not found"));
    }
}
