package com.pfe.sageline.Config;

import com.pfe.sageline.entity.User;
import com.pfe.sageline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
@RequiredArgsConstructor
@Slf4j
@Component
public class SecurityUtils {
    private final UserRepository userRepository;


    public String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
    }
    public Long getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        if (jwt == null) {
            log.warn("[SecurityUtils] No JWT in security context");
            return null;
        }

        String keycloakSub = jwt.getSubject(); // UUID string e.g. "a3f2c1d0-..."
        log.info("[SecurityUtils] Looking up DB user for Keycloak sub: {}", keycloakSub);

        return userRepository.findByKeycloakId(keycloakSub)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException(
                        "User not synced in DB for Keycloak ID: " + keycloakSub +
                                " — call GET /api/users/me first"
                ));
    }

    public String getCurrentEmail() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getCurrentRoles() {
        Jwt jwt = getCurrentJwt();
        if (jwt == null) return List.of();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();
        return (List<String>) realmAccess.getOrDefault("roles", List.of());
    }

    public boolean hasRole(String role) {
        return getCurrentRoles().contains(role);
    }

    private Jwt getCurrentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            return (Jwt) auth.getPrincipal();
        }
        return null;
    }
}