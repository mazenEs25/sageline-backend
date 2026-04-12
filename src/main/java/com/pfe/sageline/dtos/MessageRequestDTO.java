package com.pfe.sageline.dtos;

import lombok.*;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MessageRequestDTO {
    private Long conversationId;
    private Long senderId;
    private String content;
}