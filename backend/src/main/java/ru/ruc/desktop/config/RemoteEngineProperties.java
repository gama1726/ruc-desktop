package ru.ruc.desktop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.remote")
public class RemoteEngineProperties {

    /**
     * Шаблон deep link для клиента движка удалёнки. Плейсхолдер {peerId}.
     */
    private String deepLinkTemplate = "";

    public String getDeepLinkTemplate() {
        return deepLinkTemplate;
    }

    public void setDeepLinkTemplate(String deepLinkTemplate) {
        this.deepLinkTemplate = deepLinkTemplate;
    }
}
