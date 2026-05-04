package ru.ruc.desktop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "machines")
public class Machine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomCode;

    @Column(nullable = false)
    private String hostname;

    /** Идентификатор peer в выбранном движке удалёнки (симбиоз). */
    @Column(name = "engine_peer_id")
    private String enginePeerId;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getEnginePeerId() {
        return enginePeerId;
    }

    public void setEnginePeerId(String enginePeerId) {
        this.enginePeerId = enginePeerId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
