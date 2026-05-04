package ru.ruc.desktop.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.ruc.desktop.domain.Machine;

public interface MachineRepository extends JpaRepository<Machine, Long> {

    List<Machine> findByActiveTrueOrderByRoomCodeAscHostnameAsc();
}
