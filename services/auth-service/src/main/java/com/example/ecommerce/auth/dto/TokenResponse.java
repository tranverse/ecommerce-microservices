package com.example.ecommerce.auth.dto;

import com.example.ecommerce.auth.domain.AccountRole;

import java.util.Set;
import java.util.UUID;

public record TokenResponse(
        UUID accountId,
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken,
        Set<AccountRole> roles
) {
}
