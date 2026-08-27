package com.example.ecommerce.auth.service;

import com.example.ecommerce.auth.config.JwtProperties;
import com.example.ecommerce.auth.domain.AuthAccount;
import com.example.ecommerce.auth.domain.RefreshToken;
import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.dto.TokenResponse;
import com.example.ecommerce.auth.exception.EmailAlreadyRegisteredException;
import com.example.ecommerce.auth.exception.InvalidCredentialsException;
import com.example.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.example.ecommerce.auth.repository.AuthAccountRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import com.example.ecommerce.auth.security.GeneratedRefreshToken;
import com.example.ecommerce.auth.security.CredentialVerifier;
import com.example.ecommerce.auth.security.JwtTokenService;
import com.example.ecommerce.auth.security.RefreshTokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CredentialVerifier credentialVerifier;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthService(
            AuthAccountRepository accountRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            CredentialVerifier credentialVerifier,
            JwtTokenService jwtTokenService,
            RefreshTokenGenerator refreshTokenGenerator,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.credentialVerifier = credentialVerifier;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String normalizedEmail = AuthAccount.normalizeEmail(request.email());
        if (accountRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        AuthAccount account = AuthAccount.registerCustomer(
                normalizedEmail, passwordEncoder.encode(request.password()));
        AuthAccount saved = accountRepository.saveAndFlush(account);
        log.info("Registered authentication account accountId={}", saved.getId());
        return issueTokens(saved);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = AuthAccount.normalizeEmail(request.email());
        var accountResult = accountRepository.findByEmail(normalizedEmail);
        if (accountResult.isEmpty()) {
            credentialVerifier.performDummyCheck(request.password());
            throw new InvalidCredentialsException();
        }
        AuthAccount account = accountResult.get();
        if (!account.isActive() || !credentialVerifier.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        log.info("Authenticated account accountId={}", account.getId());
        return issueTokens(account);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Instant now = clock.instant();
        String tokenHash = refreshTokenGenerator.hash(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!existing.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        existing.revoke(now);
        return issueTokens(existing.getAccount());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String tokenHash = refreshTokenGenerator.hash(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private TokenResponse issueTokens(AuthAccount account) {
        Instant now = clock.instant();
        GeneratedRefreshToken generated = refreshTokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                account,
                generated.hash(),
                now.plus(jwtProperties.refreshTokenTtl()),
                now
        );
        refreshTokenRepository.saveAndFlush(refreshToken);
        String accessToken = jwtTokenService.createAccessToken(account);
        return new TokenResponse(
                account.getId(),
                "Bearer",
                accessToken,
                jwtProperties.accessTokenTtl().toSeconds(),
                generated.rawValue(),
                account.getRoles()
        );
    }
}
