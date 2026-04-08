package com.pfe.sageline.repository;
import com.pfe.sageline.entity.Role;
import com.pfe.sageline.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByRole(Role role);

    List<User> findByProductionLineId(Long productionLineId);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
