package com.example.ecommerce.auth.repository;

import com.example.ecommerce.auth.domain.AuthAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<AuthAccount> findByEmail(String email);
}
