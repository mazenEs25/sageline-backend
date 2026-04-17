package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.NotificationType;
import lombok.*;
import java.time.LocalDateTime;
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class NotificationResponseDTO {
    private Long id;
    private String title;
    private String content;
    private NotificationType notificationType;
    private Long referenceId;
    private String referenceType;
    private boolean read;
    private LocalDateTime createdAt;
}