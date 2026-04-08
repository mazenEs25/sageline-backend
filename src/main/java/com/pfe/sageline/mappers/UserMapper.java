package com.pfe.sageline.mappers;


import com.pfe.sageline.dtos.UserRequestDTO;
import com.pfe.sageline.dtos.UserResponseDTO;
import com.pfe.sageline.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    
    public UserResponseDTO toResponseDTO(User user) {
        if (user == null) {
            return null;
        }
        
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        
        if (user.getProductionLine() != null) {
            dto.setProductionLineId(user.getProductionLine().getId());
            dto.setProductionLineName(user.getProductionLine().getName());
        }
        
        return dto;
    }
    
    public User toEntity(UserRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
        
        return user;
    }
    
    public void updateEntityFromDTO(UserRequestDTO dto, User user) {
        if (dto == null || user == null) {
            return;
        }
        
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole());
    }
}
