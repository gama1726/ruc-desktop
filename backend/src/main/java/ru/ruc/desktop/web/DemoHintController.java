package ru.ruc.desktop.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ruc.desktop.config.DemoProperties;
import ru.ruc.desktop.config.RemoteEngineProperties;
import ru.ruc.desktop.web.dto.EngineHintResponse;

@RestController
@RequestMapping("/api/demo")
public class DemoHintController {

    private final DemoProperties demo;
    private final RemoteEngineProperties remote;

    public DemoHintController(DemoProperties demo, RemoteEngineProperties remote) {
        this.demo = demo;
        this.remote = remote;
    }

    @GetMapping("/engine-hint")
    public EngineHintResponse engineHint() {
        List<String> peers = List.of(demo.getFirstPeer(), demo.getSecondPeer());
        List<String> steps =
                List.of(
                        "Запустите relay: в каталоге ruc-desktop выполните "
                                + "`docker compose -f docker-compose.engine-relay.yml up -d`.",
                        "Откройте файл " + demo.getEngineKeyFileHint() + " и скопируйте ключ (одна строка).",
                        "В клиенте удалённого доступа на обоих ПК: Настройки → Сеть → ID-сервер = "
                                + demo.getEngineIdServer()
                                + ", вставьте ключ.",
                        "На удалённом ПК посмотрите ID — внесите этот же ID в колонку «Peer» "
                                + "в адресной книге РУК Коннект (или задайте переменные RUC_DEMO_PEER_FIRST / SECOND до первого запуска бэка).",
                        "В портале: Подключиться → «Открыть в клиенте» или введите ID в клиенте вручную.");
        return new EngineHintResponse(
                demo.getEngineIdServer(),
                demo.getEngineRelayAddress(),
                demo.getEngineKeyFileHint(),
                remote.getDeepLinkTemplate(),
                peers,
                steps);
    }
}
