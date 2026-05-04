package ru.ruc.desktop.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RemoteEngineProperties.class, DemoProperties.class})
public class AppConfig {
}
