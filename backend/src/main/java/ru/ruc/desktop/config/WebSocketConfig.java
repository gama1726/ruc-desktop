package ru.ruc.desktop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import ru.ruc.desktop.web.ws.MediaWebSocketHandler;
import ru.ruc.desktop.web.ws.SignalingWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Bean
    public ServletServerContainerFactoryBean webSocketContainer(
            @Value("${app.websocket.max-text-message-size:8388608}") int maxTextMessageSize) {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageSize);
        container.setMaxBinaryMessageBufferSize(maxTextMessageSize);
        return container;
    }

    private final SignalingWebSocketHandler signalingWebSocketHandler;
    private final MediaWebSocketHandler mediaWebSocketHandler;

    public WebSocketConfig(
            SignalingWebSocketHandler signalingWebSocketHandler,
            MediaWebSocketHandler mediaWebSocketHandler) {
        this.signalingWebSocketHandler = signalingWebSocketHandler;
        this.mediaWebSocketHandler = mediaWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingWebSocketHandler, "/ws/signaling").setAllowedOrigins("*");
        registry.addHandler(mediaWebSocketHandler, "/ws/media").setAllowedOrigins("*");
    }
}
