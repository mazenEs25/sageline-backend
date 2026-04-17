package com.pfe.sageline.controller;


import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.UserRequestDTO;
import com.pfe.sageline.dtos.response.UserResponseDTO;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.mappers.UserMapper;
import com.pfe.sageline.repository.UserRepository;
import com.pfe.sageline.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints pour la gestion des utilisateurs")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR','EXPERT', 'TECH_VAL', 'TECH_PREP', 'RESPONSABLE')")
    @Operation(summary = "Récupérer tous les utilisateurs")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        List<UserResponseDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR','EXPERT', 'TECH_VAL', 'TECH_PREP', 'RESPONSABLE')")
    @Operation(summary = "Récupérer un utilisateur par ID")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        UserResponseDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR','EXPERT', 'TECH_VAL', 'TECH_PREP', 'RESPONSABLE')")
    @Operation(summary = "Récupérer un utilisateur par username")
    public ResponseEntity<UserResponseDTO> getUserByUsername(@PathVariable String username) {
        UserResponseDTO user = userService.getUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/secteur/{secteurId}")
    @PreAuthorize("hasAnyRole('ADMIN_IT', 'CHEF_SECTEUR')")
    @Operation(summary = "Récupérer les utilisateurs d'un secteur")
    public ResponseEntity<List<UserResponseDTO>> getUsersBySecteur(@PathVariable Long secteurId) {
        List<UserResponseDTO> users = userService.getUsersBySecteur(secteurId);
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Créer un nouvel utilisateur")
    public ResponseEntity<UserResponseDTO> createUser(@Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO createdUser = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Modifier un utilisateur")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO request) {
        UserResponseDTO updatedUser = userService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Supprimer un utilisateur")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exists/username/{username}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Vérifier si un username existe")
    public ResponseEntity<Boolean> checkUsernameExists(@PathVariable String username) {
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/exists/email/{email}")
    @PreAuthorize("hasRole('ADMIN_IT')")
    @Operation(summary = "Vérifier si un email existe")
    public ResponseEntity<Boolean> checkEmailExists(@PathVariable String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }
    @GetMapping("/messaging-contacts")
    @Operation(summary = "Récupérer tous les utilisateurs disponibles pour la messagerie (hors soi-même)")
    public ResponseEntity<List<UserResponseDTO>> getMessagingContacts() {
        Long currentUserId = securityUtils.getCurrentUserId();
        List<UserResponseDTO> contacts = userService.getAllUsers().stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .toList();
        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        try {
            Long userId = securityUtils.getCurrentUserId();
            return ResponseEntity.ok(userService.getUserById(userId));
        } catch (Exception e) {
            // User exists in Keycloak but not in PostgreSQL
            // Auto-create them from the JWT token
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Jwt jwt = (Jwt) auth.getPrincipal();

            String username = jwt.getClaimAsString("preferred_username");
            String email = jwt.getClaimAsString("email");
            String firstName = jwt.getClaimAsString("given_name");
            String lastName = jwt.getClaimAsString("family_name");
            String keycloakId = jwt.getSubject();

            // Extract role from realm_access
            String role = "TECH_VAL"; // default
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                var roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null) {
                    for (String r : roles) {
                        if (r.equals("ADMIN_IT") || r.equals("CHEF_SECTEUR") ||
                                r.equals("EXPERT") || r.equals("TECH_VAL") ||
                                r.equals("TECH_PREP") || r.equals("RESPONSABLE")) {
                            role = r;
                            break;
                        }
                    }
                }
            }
            // Create user in PostgreSQL
            User newUser = User.builder()
                    .username(username)
                    .email(email != null ? email : username + "@sageline.com")
                    .firstName(firstName)
                    .lastName(lastName)
                    .keycloakId(keycloakId)
                    .role(com.pfe.sageline.enums.Role.valueOf(role))
                    .build();

            newUser = userRepository.save(newUser);

            return ResponseEntity.ok(userMapper.toResponseDTO(newUser));
        }
    }

}
