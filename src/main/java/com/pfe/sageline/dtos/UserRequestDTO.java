package com.pfe.sageline.dtos;


import com.pfe.sageline.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDTO {
    
    @NotBlank(message = "Username is required")
    private String username;
    // New field for Keycloak
    private String password;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    @NotNull(message = "Role is required")
    private Role role;
    
    private Long productionLineId;
}
