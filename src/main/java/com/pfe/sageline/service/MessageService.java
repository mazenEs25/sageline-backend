package com.pfe.sageline.service;

import com.pfe.sageline.dtos.request.MessageRequestDTO;
import com.pfe.sageline.dtos.response.ConversationResponseDTO;
import com.pfe.sageline.dtos.response.MessageResponseDTO;
import com.pfe.sageline.entity.*;
import com.pfe.sageline.enums.ConversationType;
import com.pfe.sageline.enums.MessageType;
import com.pfe.sageline.enums.NotificationType;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    // =====================================================
    // CONVERSATIONS
    // =====================================================

    public List<ConversationResponseDTO> getUserConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findByUserId(userId);
        return conversations.stream()
                .map(c -> toConversationDTO(c, userId))
                .collect(Collectors.toList());
    }

    public ConversationResponseDTO getOrCreateConversation(Long userOneId, Long userTwoId) {
        Conversation conversation = conversationRepository
                .findByParticipants(userOneId, userTwoId)
                .orElseGet(() -> createConversation(userOneId, userTwoId,
                        ConversationType.DIRECT, null, null));

        return toConversationDTO(conversation, userOneId);
    }

    public Conversation createConversation(Long userOneId, Long userTwoId,
                                           ConversationType type,
                                           Long referenceId, String referenceType) {
        User userOne = userRepository.findById(userOneId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userOneId));
        User userTwo = userRepository.findById(userTwoId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userTwoId));

        Conversation conversation = Conversation.builder()
                .participantOne(userOne)
                .participantTwo(userTwo)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();

        return conversationRepository.save(conversation);
    }

    // =====================================================
    // MESSAGES
    // =====================================================

    public MessageResponseDTO sendMessage(MessageRequestDTO request) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", "id", request.getConversationId()));

        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", request.getSenderId()));

        Message message = Message.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .messageType(MessageType.TEXT)
                .systemMessage(false)
                .build();

        Message saved = messageRepository.save(message);

        // Mettre à jour le timestamp de la conversation
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        MessageResponseDTO dto = toMessageDTO(saved);

        // Envoyer via WebSocket au topic de la conversation
        messagingTemplate.convertAndSend(
                "/topic/chat." + conversation.getId(), dto);

        // Créer une notification persistante et l'envoyer via WebSocket
        Long recipientId = getRecipientId(conversation, request.getSenderId());
        notificationService.createAndSend(
                recipientId,
                "Nouveau message de " + sender.getUsername(),
                truncate(request.getContent(), 80),
                NotificationType.NEW_MESSAGE,
                conversation.getId(),
                "CONVERSATION"
        );

        return dto;
    }

    public MessageResponseDTO sendSystemMessage(Long conversationId, String content,
                                                MessageType type, Long systemUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Conversation", "id", conversationId));

        User systemUser = userRepository.findById(systemUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", systemUserId));

        Message message = Message.builder()
                .conversation(conversation)
                .sender(systemUser)
                .content(content)
                .messageType(type)
                .systemMessage(true)
                .build();

        Message saved = messageRepository.save(message);
        MessageResponseDTO dto = toMessageDTO(saved);

        messagingTemplate.convertAndSend(
                "/topic/chat." + conversationId, dto);

        return dto;
    }

    public List<MessageResponseDTO> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)
                .stream()
                .map(this::toMessageDTO)
                .collect(Collectors.toList());
    }

    public void markConversationAsRead(Long conversationId, Long userId) {
        int updated = messageRepository.markAsRead(conversationId, userId, LocalDateTime.now());
        if (updated > 0) {
            // Informer l'expéditeur que ses messages ont été lus
            messagingTemplate.convertAndSend(
                    "/topic/chat." + conversationId,
                    (Object) Map.of("type", "READ_RECEIPT",
                            "conversationId", conversationId,
                            "readBy", userId,
                            "readAt", LocalDateTime.now().toString()));
        }
    }

    public int getUnreadCount(Long userId) {
        return messageRepository.countTotalUnread(userId);
    }

    /**
     * Send an automatic system message between two users.
     * Creates a conversation if one does not already exist.
     */
    public void sendAutoMessage(Long senderId, Long receiverId, String content) {
        ConversationResponseDTO conv = getOrCreateConversation(senderId, receiverId);
        sendSystemMessage(conv.getId(), content, MessageType.VALIDATION_ASSIGNMENT, senderId);
    }

    /**
     * Send an automatic system message for a validation ticket assignment.
     * Creates or reuses a VALIDATION_ASSIGNMENT conversation linked to the ticket.
     * After sending the message, broadcasts a refresh signal to both participants'
     * notification channels so their conversation lists update in real-time.
     */
    public void sendAutoMessageForValidation(Long senderId, Long receiverId,
                                              String content, Long validationId) {
        // Try to find an existing conversation linked to this validation
        Conversation conversation = conversationRepository
                .findByParticipantsAndReference(senderId, receiverId, validationId, "VALIDATION")
                .orElseGet(() -> createConversation(
                        senderId, receiverId,
                        ConversationType.VALIDATION_ASSIGNMENT,
                        validationId, "VALIDATION"));

        sendSystemMessage(conversation.getId(), content,
                MessageType.VALIDATION_ASSIGNMENT, senderId);

        // Broadcast a lightweight "new conversation" signal to both users so their
        // conversation list components reload immediately without needing a page refresh.
        Map<String, Object> refreshPayload = Map.of(
                "notificationType", "NEW_CONVERSATION",
                "conversationId", conversation.getId()
        );
        messagingTemplate.convertAndSend("/topic/notifications." + senderId, (Object) refreshPayload);
        messagingTemplate.convertAndSend("/topic/notifications." + receiverId, (Object) refreshPayload);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private Long getRecipientId(Conversation conversation, Long senderId) {
        return conversation.getParticipantOne().getId().equals(senderId)
                ? conversation.getParticipantTwo().getId()
                : conversation.getParticipantOne().getId();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    private ConversationResponseDTO toConversationDTO(Conversation c, Long currentUserId) {
        // Use repo query instead of c.getMessages() to avoid LazyInitializationException
        // on freshly-saved entities where the Hibernate proxy is not yet initialized.
        Message lastMessage = null;
        int unreadCount = 0;
        try {
            List<Message> messages = messageRepository.findByConversationIdOrderBySentAtAsc(c.getId());
            if (messages != null && !messages.isEmpty()) {
                lastMessage = messages.get(messages.size() - 1);
            }
            unreadCount = messageRepository.countUnreadInConversation(c.getId(), currentUserId);
        } catch (Exception e) {
            log.warn("Could not load messages for conversation {}: {}", c.getId(), e.getMessage());
        }

        return ConversationResponseDTO.builder()
                .id(c.getId())
                .participantOneId(c.getParticipantOne().getId())
                .participantOneUsername(c.getParticipantOne().getUsername())
                .participantOneRole(c.getParticipantOne().getRole() != null
                        ? c.getParticipantOne().getRole().name() : "UNKNOWN")
                .participantTwoId(c.getParticipantTwo().getId())
                .participantTwoUsername(c.getParticipantTwo().getUsername())
                .participantTwoRole(c.getParticipantTwo().getRole() != null
                        ? c.getParticipantTwo().getRole().name() : "UNKNOWN")
                .type(c.getType())
                .referenceId(c.getReferenceId())
                .referenceType(c.getReferenceType())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .lastMessageContent(lastMessage != null ? truncate(lastMessage.getContent(), 80) : null)
                .lastMessageAt(lastMessage != null ? lastMessage.getSentAt() : null)
                .lastMessageSender(lastMessage != null ? lastMessage.getSender().getUsername() : null)
                .unreadCount(unreadCount)
                .build();
    }

    private MessageResponseDTO toMessageDTO(Message m) {
        return MessageResponseDTO.builder()
                .id(m.getId())
                .conversationId(m.getConversation().getId())
                .senderId(m.getSender().getId())
                .senderUsername(m.getSender().getUsername())
                .senderRole(m.getSender().getRole().name())
                .content(m.getContent())
                .messageType(m.getMessageType())
                .sentAt(m.getSentAt())
                .readAt(m.getReadAt())
                .systemMessage(m.isSystemMessage())
                .build();
    }
}