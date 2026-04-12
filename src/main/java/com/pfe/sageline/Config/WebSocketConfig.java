package com.pfe.sageline.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Le broker simple pour les topics de souscription
        config.enableSimpleBroker("/topic", "/queue");

        // Préfixe pour les messages envoyés par le client vers le serveur
        config.setApplicationDestinationPrefixes("/app");

        // Préfixe pour les messages destinés à un utilisateur spécifique
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Point d'entrée WebSocket avec fallback SockJS
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:4200")
                .withSockJS();
    }
}