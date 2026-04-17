package com.pfe.sageline.repository;
import com.pfe.sageline.enums.Role;
import com.pfe.sageline.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByKeycloakId(String keycloakId);

    List<User> findByRole(Role role);

    List<User> findBySecteurId(Long secteurId);

    List<User> findByRoleAndSecteurId(Role role, Long secteurId);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
