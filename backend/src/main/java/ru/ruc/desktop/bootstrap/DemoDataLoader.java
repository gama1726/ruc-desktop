package ru.ruc.desktop.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.ruc.desktop.domain.Machine;
import ru.ruc.desktop.repository.MachineRepository;

@Component
public class DemoDataLoader implements ApplicationRunner {

    private final MachineRepository machineRepository;

    public DemoDataLoader(MachineRepository machineRepository) {
        this.machineRepository = machineRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (machineRepository.count() > 0) {
            return;
        }
        Machine a = new Machine();
        a.setRoomCode("А-101");
        a.setHostname("pc-lab-a101-01");
        a.setEnginePeerId("123456789");
        a.setActive(true);

        Machine b = new Machine();
        b.setRoomCode("Б-202");
        b.setHostname("teacher-notebook-02");
        b.setEnginePeerId(null);
        b.setActive(true);

        machineRepository.save(a);
        machineRepository.save(b);
    }
}
