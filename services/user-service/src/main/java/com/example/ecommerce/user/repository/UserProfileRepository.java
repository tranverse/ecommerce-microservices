package com.example.ecommerce.user.repository;

import com.example.ecommerce.user.domain.UserProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @EntityGraph(attributePaths = "addresses")
    @Query("select profile from UserProfile profile where profile.id = :id")
    Optional<UserProfile> findWithAddressesById(UUID id);
}
