package com.example.ecommerce.auth.repository;

import com.example.ecommerce.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @EntityGraph(attributePaths = {"account", "account.roles"})
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
