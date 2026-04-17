package com.pfe.sageline.controller;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.MessageRequestDTO;
import com.pfe.sageline.dtos.response.ConversationResponseDTO;
import com.pfe.sageline.dtos.response.MessageResponseDTO;
import com.pfe.sageline.service.MessageService;
import lombok.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SecurityUtils securityUtils;

    // --- Conversations ---

    @GetMapping("/conversations/{userId}")
    public ResponseEntity<List<ConversationResponseDTO>> getUserConversations(
            @PathVariable Long userId) {
        return ResponseEntity.ok(messageService.getUserConversations(userId));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponseDTO> getOrCreateConversation(
            @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(
                messageService.getOrCreateConversation(
                        body.get("userOneId"), body.get("userTwoId")));
    }

    @PostMapping("/conversations/with/{userId}")
    public ResponseEntity<ConversationResponseDTO> getOrCreateConversationWithUser(
            @PathVariable Long userId) {
        Long currentUserId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(messageService.getOrCreateConversation(currentUserId, userId));
    }

    // --- Messages ---

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageResponseDTO>> getMessages(
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(
                messageService.getConversationMessages(conversationId));
    }

    @PostMapping
    public ResponseEntity<MessageResponseDTO> sendMessage(
            @RequestBody MessageRequestDTO request) {
        return ResponseEntity.ok(messageService.sendMessage(request));
    }

    @PatchMapping("/conversation/{conversationId}/read/{userId}")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long conversationId,
            @PathVariable Long userId) {
        messageService.markConversationAsRead(conversationId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count/{userId}")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @PathVariable Long userId) {
        return ResponseEntity.ok(
                Map.of("count", messageService.getUnreadCount(userId)));
    }
}