package ru.ruc.desktop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public class DemoProperties {

    private String firstRoom = "Ауд. 101 — учебная лаборатория";
    private String firstHostname = "ПК преподавателя (демо)";
    private String firstPeer = "111222333";

    private String secondRoom = "Кафедра — методический кабинет";
    private String secondHostname = "Рабочая станция (демо)";
    private String secondPeer = "444555666";

    /** Хост:порт ID-сервера для подсказки в UI. */
    private String engineIdServer = "127.0.0.1:21116";

    /** Адрес relay для справки. */
    private String engineRelayAddress = "127.0.0.1:21117";

    /** Относительный путь к каталогу с ключом (рядом с compose). */
    private String engineKeyFileHint = "engine-relay-data/id_ed25519.pub";

    public String getFirstRoom() {
        return firstRoom;
    }

    public void setFirstRoom(String firstRoom) {
        this.firstRoom = firstRoom;
    }

    public String getFirstHostname() {
        return firstHostname;
    }

    public void setFirstHostname(String firstHostname) {
        this.firstHostname = firstHostname;
    }

    public String getFirstPeer() {
        return firstPeer;
    }

    public void setFirstPeer(String firstPeer) {
        this.firstPeer = firstPeer;
    }

    public String getSecondRoom() {
        return secondRoom;
    }

    public void setSecondRoom(String secondRoom) {
        this.secondRoom = secondRoom;
    }

    public String getSecondHostname() {
        return secondHostname;
    }

    public void setSecondHostname(String secondHostname) {
        this.secondHostname = secondHostname;
    }

    public String getSecondPeer() {
        return secondPeer;
    }

    public void setSecondPeer(String secondPeer) {
        this.secondPeer = secondPeer;
    }

    public String getEngineIdServer() {
        return engineIdServer;
    }

    public void setEngineIdServer(String engineIdServer) {
        this.engineIdServer = engineIdServer;
    }

    public String getEngineRelayAddress() {
        return engineRelayAddress;
    }

    public void setEngineRelayAddress(String engineRelayAddress) {
        this.engineRelayAddress = engineRelayAddress;
    }

    public String getEngineKeyFileHint() {
        return engineKeyFileHint;
    }

    public void setEngineKeyFileHint(String engineKeyFileHint) {
        this.engineKeyFileHint = engineKeyFileHint;
    }
}
