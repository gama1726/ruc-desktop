package ru.ruc.desktop.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ruc.desktop.service.MachineService;
import ru.ruc.desktop.web.dto.MachineResponse;

@RestController
@RequestMapping("/api/machines")
public class MachineController {

    private final MachineService machineService;

    public MachineController(MachineService machineService) {
        this.machineService = machineService;
    }

    @GetMapping
    public List<MachineResponse> list() {
        return machineService.listActive();
    }

    @GetMapping("/{id}")
    public MachineResponse get(@PathVariable long id) {
        return machineService.get(id);
    }
}
