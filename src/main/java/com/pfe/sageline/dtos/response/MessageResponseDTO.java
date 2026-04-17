package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.MessageType;
import lombok.*;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class MessageResponseDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderUsername;
    private String senderRole;
    private String content;
    private MessageType messageType;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private boolean systemMessage;
}