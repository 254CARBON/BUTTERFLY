package com.z254.butterfly.cortex.config;

import com.z254.butterfly.cortex.api.websocket.AgentWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

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
    public HandlerMapping webSocketHandlerMapping(AgentWebSocketHandler agentWebSocketHandler) {
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setOrder(-1);
        handlerMapping.setUrlMap(Map.of("/ws/agents", agentWebSocketHandler));
        return handlerMapping;
    }
}
