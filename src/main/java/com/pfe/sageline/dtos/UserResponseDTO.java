package com.pfe.sageline.dtos;

import com.pfe.sageline.entity.Role;
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
    private Role role;
    private Long productionLineId;
    private String productionLineName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
