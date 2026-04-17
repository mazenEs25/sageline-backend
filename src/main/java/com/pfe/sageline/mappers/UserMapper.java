package com.pfe.sageline.mappers;


import com.pfe.sageline.dtos.request.UserRequestDTO;
import com.pfe.sageline.dtos.response.UserResponseDTO;
import com.pfe.sageline.entity.Secteur;
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
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRole(user.getRole());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        Secteur secteur = user.getSecteur();
        if (secteur != null) {
            dto.setSecteurId(secteur.getId());
            dto.setSecteurCode(secteur.getCode());
            dto.setSecteurName(secteur.getName());
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
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setRole(dto.getRole());
        if (dto.getSecteurId() != null) {
            Secteur secteur = new Secteur();
            secteur.setId(dto.getSecteurId());
            user.setSecteur(secteur);
        }

        return user;
    }

    public void updateEntityFromDTO(UserRequestDTO dto, User user) {
        if (dto == null || user == null) {
            return;
        }

        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        if (dto.getFirstName() != null) {
            user.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            user.setLastName(dto.getLastName());
        }
        user.setRole(dto.getRole());
        if (dto.getSecteurId() != null) {
            Secteur secteur = new Secteur();
            secteur.setId(dto.getSecteurId());
            user.setSecteur(secteur);
        } else {
            user.setSecteur(null);
        }
    }
}
