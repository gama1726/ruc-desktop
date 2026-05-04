package ru.ruc.desktop.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.ruc.desktop.config.DemoProperties;
import ru.ruc.desktop.domain.Machine;
import ru.ruc.desktop.repository.MachineRepository;

@Component
public class DemoDataLoader implements ApplicationRunner {

    private final MachineRepository machineRepository;
    private final DemoProperties demo;

    public DemoDataLoader(MachineRepository machineRepository, DemoProperties demo) {
        this.machineRepository = machineRepository;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (machineRepository.count() > 0) {
            return;
        }
        Machine a = new Machine();
        a.setRoomCode(demo.getFirstRoom());
        a.setHostname(demo.getFirstHostname());
        a.setEnginePeerId(demo.getFirstPeer());
        a.setActive(true);

        Machine b = new Machine();
        b.setRoomCode(demo.getSecondRoom());
        b.setHostname(demo.getSecondHostname());
        b.setEnginePeerId(demo.getSecondPeer());
        b.setActive(true);

        machineRepository.save(a);
        machineRepository.save(b);
    }
}
