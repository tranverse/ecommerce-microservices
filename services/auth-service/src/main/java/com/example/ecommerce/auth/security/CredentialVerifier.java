package com.example.ecommerce.auth.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CredentialVerifier {

    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public CredentialVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    public boolean matches(String rawPassword, String storedHash) {
        return passwordEncoder.matches(rawPassword, storedHash);
    }

    public void performDummyCheck(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyHash);
    }
}
