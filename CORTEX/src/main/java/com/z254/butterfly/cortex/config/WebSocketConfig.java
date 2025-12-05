package com.z254.butterfly.cortex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket configuration for CORTEX service.
 * Enables real-time streaming of agent thoughts.
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping(
            Map<String, WebSocketHandler> webSocketHandlers) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        
        // Register WebSocket handlers
        webSocketHandlers.forEach((path, handler) -> {
            map.put(path, handler);
        });

        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(1);
        handlerMapping.setUrlMap(map);
        return handlerMapping;
    }

    @Bean
    public Map<String, WebSocketHandler> webSocketHandlers() {
        // Handlers will be added by AgentWebSocketHandler component
        return new HashMap<>();
    }
}
