package ru.ruc.desktop.web.dto;

public record MachineResponse(
        Long id,
        String roomCode,
        String hostname,
        String enginePeerId,
        boolean active) {

    public static MachineResponse from(ru.ruc.desktop.domain.Machine m) {
        return new MachineResponse(
                m.getId(), m.getRoomCode(), m.getHostname(), m.getEnginePeerId(), m.isActive());
    }
}
