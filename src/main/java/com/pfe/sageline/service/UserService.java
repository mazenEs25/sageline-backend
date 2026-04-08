package com.pfe.sageline.service;

import com.pfe.sageline.dtos.UserRequestDTO;
import com.pfe.sageline.dtos.UserResponseDTO;
import com.pfe.sageline.entity.ProductionLine;
import com.pfe.sageline.entity.User;
import com.pfe.sageline.entity.Role;
import com.pfe.sageline.exception.ResourceNotFoundException;
import com.pfe.sageline.exception.ValidationException;
import com.pfe.sageline.mappers.UserMapper;
import com.pfe.sageline.repository.ProductionLineRepository;
import com.pfe.sageline.repository.UserRepository;
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

    public UserService(UserRepository userRepository,
                       ProductionLineRepository lineRepository,
                       UserMapper userMapper,
                       KeycloakUserService keycloakUserService) {
        this.userRepository = userRepository;
        this.lineRepository = lineRepository;
        this.userMapper = userMapper;
        this.keycloakUserService = keycloakUserService;
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
                    request.getUsername(),   // firstName = username
                    request.getRole().name()
            );
        } catch (Exception e) {
            throw new ValidationException("Erreur Keycloak: " + e.getMessage());
        }

        // 2. Create in database
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        if (request.getProductionLineId() != null) {
            ProductionLine line = lineRepository.findById(request.getProductionLineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ligne non trouvée"));
            user.setProductionLine(line);
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
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getProductionLineId() != null) {
            ProductionLine line = lineRepository.findById(request.getProductionLineId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ligne non trouvée"));
            user.setProductionLine(line);
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

    public List<UserResponseDTO> getUsersByProductionLine(Long lineId) {
        return userRepository.findByProductionLineId(lineId).stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }}