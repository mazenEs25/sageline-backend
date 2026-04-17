package com.pfe.sageline.dtos.request;


import com.pfe.sageline.enums.Role;
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

    private String firstName;

    private String lastName;
    
    @NotNull(message = "Role is required")
    private Role role;

    private Long secteurId;
}
