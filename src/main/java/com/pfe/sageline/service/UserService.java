package com.pfe.sageline.service;

import com.pfe.sageline.Config.SecurityUtils;
import com.pfe.sageline.dtos.request.UserRequestDTO;
import com.pfe.sageline.dtos.response.UserResponseDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.Secteur;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.enums.Role;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.UserMapper;
import com.pfe.sageline.repository.ProductionLineRepository;
import com.pfe.sageline.repository.SecteurRepository;
import com.pfe.sageline.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final ProductionLineRepository lineRepository;
    private final UserMapper userMapper;
    private final KeycloakUserService keycloakUserService;
    private final SecteurRepository secteurRepository;

    @Autowired
    private MessagingEventService messagingEventService;
    @Autowired
    private SecurityUtils securityUtils;

    public UserService(UserRepository userRepository,
                       ProductionLineRepository lineRepository,
                       UserMapper userMapper,
                       KeycloakUserService keycloakUserService,
                       SecteurRepository secteurRepository) {
        this.userRepository = userRepository;
        this.lineRepository = lineRepository;
        this.userMapper = userMapper;
        this.keycloakUserService = keycloakUserService;
        this.secteurRepository =secteurRepository;
    }

    /**
     * Récupère l'utilisateur actuellement authentifié (depuis le JWT Keycloak).
     * Retourne null si aucun utilisateur n'est authentifié ou introuvable en base.
     */
    private User getCurrentUser() {
        String username = securityUtils.getCurrentUsername();
        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    // ─── CREATE (Keycloak + DB) ───
    public UserResponseDTO createUser(UserRequestDTO request) {
        // Validate
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new ValidationException("Le nom d'utilisateur est obligatoire");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("L'email est obligatoire");
        }
        if (request.getPassword() == null || request.getPassword().length() < 4) {
            throw new ValidationException("Le mot de passe doit contenir au moins 4 caractères");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ValidationException("Ce nom d'utilisateur existe déjà");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ValidationException("Cet email existe déjà");
        }

        // 1. Create in Keycloak FIRST
        try {
            keycloakUserService.createUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName() != null ? request.getFirstName() : request.getUsername(),
                    request.getLastName(),
                    request.getRole().name()
            );
        } catch (Exception e) {
            throw new ValidationException("Erreur Keycloak: " + e.getMessage());
        }

        // 2. Create in database
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(request.getRole());

        if (request.getSecteurId() != null) {
            Secteur secteur = secteurRepository.findById(request.getSecteurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Secteur non trouvé"));
            user.setSecteur(secteur);
        }
        User saved = userRepository.save(user);

        return userMapper.toResponseDTO(saved);
    }

    // ─── UPDATE (Keycloak + DB) ───
    public UserResponseDTO updateUser(Long id, UserRequestDTO request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        String oldUsername = user.getUsername();
        Role oldRole = user.getRole();
        String oldEmail = user.getEmail();

        // Update database fields
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getSecteurId() != null) {
            Secteur secteur = secteurRepository.findById(request.getSecteurId())
                    .orElseThrow(() -> new ResourceNotFoundException("Secteur non trouvé"));
            user.setSecteur(secteur);
        }
        // Sync with Keycloak
        try {
            // Update role if changed
            if (request.getRole() != null && request.getRole() != oldRole) {
                keycloakUserService.updateUserRole(oldUsername, request.getRole().name());
            }
            // Update email if changed
            if (request.getEmail() != null && !request.getEmail().equals(oldEmail)) {
                keycloakUserService.updateEmail(oldUsername, request.getEmail());
            }
            // Update password if provided
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                keycloakUserService.updatePassword(oldUsername, request.getPassword());
            }
        } catch (Exception e) {
            System.err.println("WARNING: Keycloak sync failed for user "
                    + oldUsername + ": " + e.getMessage());
        }

        User saved = userRepository.save(user);

        // Si la ligne a changé → déclencher l'événement de messagerie



        return userMapper.toResponseDTO(saved);
    }

    // ─── DELETE (Keycloak + DB) ───
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));

        // Delete from Keycloak
        try {
            keycloakUserService.deleteUser(user.getUsername());
        } catch (Exception e) {
            System.err.println("WARNING: Failed to delete user from Keycloak: " + e.getMessage());
        }

        // Delete from database
        userRepository.delete(user);
    }

    // ─── READ (DB only — no Keycloak needed) ───
    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
        return userMapper.toResponseDTO(user);
    }

    public UserResponseDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur '" + username + "' non trouvé"));
        return userMapper.toResponseDTO(user);
    }

    public List<UserResponseDTO> getUsersBySecteur(Long secteurId) {
        return userRepository.findBySecteurId(secteurId).stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }}