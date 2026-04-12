package com.pfe.sageline.dtos;

import com.pfe.sageline.entity.ConversationType;
import lombok.*;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ConversationResponseDTO {
    private Long id;
    private Long participantOneId;
    private String participantOneUsername;
    private String participantOneRole;
    private Long participantTwoId;
    private String participantTwoUsername;
    private String participantTwoRole;
    private ConversationType type;
    private Long referenceId;
    private String referenceType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Dernier message (aperçu)
    private String lastMessageContent;
    private LocalDateTime lastMessageAt;
    private String lastMessageSender;

    // Compteur non lus
    private int unreadCount;
}