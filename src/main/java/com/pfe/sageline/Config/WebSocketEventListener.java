package com.pfe.sageline.Config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    // Suivi des utilisateurs connectés : userId → Set<sessionId>
    private static final Map<Long, Set<String>> onlineUsers = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        // Le userId sera mis à jour côté client après connexion via un message /app/status.connect
        log.info("Nouvelle connexion WebSocket — session: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Retirer la session de l'utilisateur
        onlineUsers.forEach((userId, sessions) -> {
            if (sessions.remove(sessionId) && sessions.isEmpty()) {
                onlineUsers.remove(userId);
                // Notifier tout le monde que l'utilisateur est déconnecté
                messagingTemplate.convertAndSend("/topic/status",
                        (Object) Map.of("userId", userId, "status", "OFFLINE"));
                log.info("Utilisateur {} déconnecté", userId);
            }
        });
    }

    public static void registerUser(Long userId, String sessionId) {
        onlineUsers.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public static boolean isUserOnline(Long userId) {
        Set<String> sessions = onlineUsers.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public static Set<Long> getOnlineUserIds() {
        return onlineUsers.keySet();
    }
}