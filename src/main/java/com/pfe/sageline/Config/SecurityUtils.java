package com.pfe.sageline.Config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SecurityUtils {

    public String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        return jwt != null ? jwt.getClaimAsString("preferred_username") : null;
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