package ru.ruc.desktop.service;

import org.springframework.stereotype.Component;
import ru.ruc.desktop.config.RemoteEngineProperties;
import ru.ruc.desktop.domain.Machine;

@Component
public class ConnectionHintBuilder {

    private final RemoteEngineProperties props;

    public ConnectionHintBuilder(RemoteEngineProperties props) {
        this.props = props;
    }

    public String deepLink(Machine machine) {
        String peerId = machine.getEnginePeerId();
        return deepLinkFromPeer(peerId);
    }

    public String deepLinkFromPeer(String peerId) {
        String template = props.getDeepLinkTemplate();
        if (peerId == null || peerId.isBlank() || template == null || template.isBlank()) {
            return null;
        }
        return template.replace("{peerId}", peerId);
    }

    public String hint(Machine machine) {
        String link = deepLink(machine);
        if (link != null) {
            return quickConnectHint();
        }
        if (machine.getEnginePeerId() == null || machine.getEnginePeerId().isBlank()) {
            return "Для этой машины не задан engine_peer_id — укажите идентификатор в движке (симбиоз).";
        }
        return "Задайте app.remote.deep-link-template в конфигурации бэкенда.";
    }

    public String quickConnectHint() {
        return "1) Установите клиент удалённого доступа (выбранный вузом). "
                + "2) Нажмите «Открыть в клиенте» или вставьте ID вручную в клиенте.";
    }
}
