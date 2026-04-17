package com.pfe.sageline.dtos.response;

import com.pfe.sageline.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private Long secteurId;
    private String secteurCode;
    private String secteurName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
