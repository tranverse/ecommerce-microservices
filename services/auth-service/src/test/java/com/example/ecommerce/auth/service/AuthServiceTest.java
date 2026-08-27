package com.example.ecommerce.auth.service;

import com.example.ecommerce.auth.config.JwtProperties;
import com.example.ecommerce.auth.domain.AuthAccount;
import com.example.ecommerce.auth.domain.RefreshToken;
import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.exception.EmailAlreadyRegisteredException;
import com.example.ecommerce.auth.exception.InvalidCredentialsException;
import com.example.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.example.ecommerce.auth.repository.AuthAccountRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import com.example.ecommerce.auth.security.GeneratedRefreshToken;
import com.example.ecommerce.auth.security.CredentialVerifier;
import com.example.ecommerce.auth.security.JwtTokenService;
import com.example.ecommerce.auth.security.RefreshTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AuthAccountRepository accountRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CredentialVerifier credentialVerifier;
    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "issuer", Duration.ofMinutes(15), Duration.ofDays(30),
                "kid", "", "", false);
        authService = new AuthService(
                accountRepository,
                refreshTokenRepository,
                passwordEncoder,
                credentialVerifier,
                jwtTokenService,
                refreshTokenGenerator,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registersWithAHashAndNeverPersistsTheRawPassword() {
        when(accountRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("correct horse battery staple")).thenReturn("bcrypt-value");
        when(accountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenGenerator.generate()).thenReturn(new GeneratedRefreshToken("raw-refresh", "a".repeat(64)));
        when(refreshTokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.createAccessToken(any())).thenReturn("signed-jwt");

        var response = authService.register(new RegisterRequest(
                "Alice@Example.com", "correct horse battery staple"));

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        verify(passwordEncoder).encode("correct horse battery staple");
        verify(accountRepository).saveAndFlush(any(AuthAccount.class));
    }

    @Test
    void rejectsDuplicateRegistrationBeforeHashing() {
        when(accountRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "alice@example.com", "correct horse battery staple")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void rejectsWrongCredentialsWithOneGenericError() {
        AuthAccount account = AuthAccount.registerCustomer("alice@example.com", "stored-hash");
        when(accountRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(account));
        when(credentialVerifier.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is invalid");
    }

    @Test
    void performsAHashCheckWhenTheEmailDoesNotExist() {
        when(accountRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(credentialVerifier).performDummyCheck("wrong");
    }

    @Test
    void rotatesAUsableRefreshToken() {
        AuthAccount account = AuthAccount.registerCustomer("alice@example.com", "stored-hash");
        RefreshToken existing = RefreshToken.issue(account, "b".repeat(64), NOW.plusSeconds(60), NOW.minusSeconds(1));
        when(refreshTokenGenerator.hash("old-token")).thenReturn("b".repeat(64));
        when(refreshTokenRepository.findByTokenHash("b".repeat(64))).thenReturn(Optional.of(existing));
        when(refreshTokenGenerator.generate()).thenReturn(new GeneratedRefreshToken("new-token", "c".repeat(64)));
        when(refreshTokenRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenService.createAccessToken(account)).thenReturn("new-jwt");

        var response = authService.refresh(new RefreshRequest("old-token"));

        assertThat(existing.getRevokedAt()).isEqualTo(NOW);
        assertThat(response.refreshToken()).isEqualTo("new-token");
    }

    @Test
    void rejectsARevokedRefreshToken() {
        AuthAccount account = AuthAccount.registerCustomer("alice@example.com", "stored-hash");
        RefreshToken existing = RefreshToken.issue(account, "b".repeat(64), NOW.plusSeconds(60), NOW.minusSeconds(1));
        existing.revoke(NOW.minusMillis(1));
        when(refreshTokenGenerator.hash("old-token")).thenReturn("b".repeat(64));
        when(refreshTokenRepository.findByTokenHash("b".repeat(64))).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
