package com.pfe.sageline.controller;

import com.pfe.sageline.Config.WebSocketEventListener;
import com.pfe.sageline.dtos.WebSocketMessageDTO;
import com.pfe.sageline.dtos.MessageRequestDTO;
import com.pfe.sageline.dtos.MessageResponseDTO;
import com.pfe.sageline.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Envoyer un message dans une conversation
     * Client envoie vers: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void sendMessage(MessageRequestDTO request) {
        if (request.getConversationId() == null || request.getSenderId() == null) {
            return;
        }
        // Le MessageService gère la persistance + broadcast WebSocket
        messageService.sendMessage(request);
    }

    /**
     * Typing indicator
     * Client envoie vers: /app/chat.typing
     */
    @MessageMapping("/chat.typing")
    public void typing(WebSocketMessageDTO message) {
        if (message.getSenderId() == null || message.getConversationId() == null) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/chat." + message.getConversationId(),
                (Object) Map.of(
                        "type", "TYPING",
                        "senderId", message.getSenderId(),
                        "conversationId", message.getConversationId(),
                        "timestamp", LocalDateTime.now().toString()
                ));
    }

    /**
     * Marquer la conversation comme lue
     * Client envoie vers: /app/chat.read
     */
    @MessageMapping("/chat.read")
    public void markAsRead(WebSocketMessageDTO message) {
        if (message.getConversationId() == null || message.getSenderId() == null) {
            return;
        }
        messageService.markConversationAsRead(
                message.getConversationId(), message.getSenderId());
    }

    /**
     * Connexion d'un utilisateur (enregistrer présence)
     * Client envoie vers: /app/status.connect
     */
    @MessageMapping("/status.connect")
    public void userConnected(WebSocketMessageDTO message,
                              SimpMessageHeaderAccessor headerAccessor) {
        if (message.getSenderId() == null) {
            return;
        }
        String sessionId = headerAccessor.getSessionId();
        WebSocketEventListener.registerUser(message.getSenderId(), sessionId);

        // Broadcast le statut en ligne
        messagingTemplate.convertAndSend("/topic/status",
                (Object) Map.of("userId", message.getSenderId(),
                        "status", "ONLINE"));
    }

    /**
     * Récupérer la liste des utilisateurs en ligne
     * Client envoie vers: /app/status.online
     */
    @MessageMapping("/status.online")
    public void getOnlineUsers(WebSocketMessageDTO message) {
        Set<Long> onlineIds = WebSocketEventListener.getOnlineUserIds();
        messagingTemplate.convertAndSend(
                "/topic/notifications." + message.getSenderId(),
                (Object) Map.of("type", "ONLINE_USERS", "userIds", onlineIds));
    }
}