package com.pfe.sageline.dtos;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessageDTO {
    private String type;    // "CHAT", "NOTIFICATION", "TYPING", "READ_RECEIPT"
    private Object payload;
    private Long senderId;
    private Long recipientId;
    private Long conversationId;
    private String timestamp;
}