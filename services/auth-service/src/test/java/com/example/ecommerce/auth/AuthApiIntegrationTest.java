package com.example.ecommerce.auth;

import com.example.ecommerce.auth.domain.AccountRole;
import com.example.ecommerce.auth.domain.AuthAccount;
import com.example.ecommerce.auth.dto.LoginRequest;
import com.example.ecommerce.auth.dto.RefreshRequest;
import com.example.ecommerce.auth.dto.RegisterRequest;
import com.example.ecommerce.auth.dto.TokenResponse;
import com.example.ecommerce.auth.exception.ApiErrorResponse;
import com.example.ecommerce.auth.repository.AuthAccountRepository;
import com.example.ecommerce.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private AuthAccountRepository accountRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void registerLoginRefreshAndLogoutEnforceTheTokenLifecycle() {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "correct horse battery staple";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "auth-integration-flow");

        ResponseEntity<TokenResponse> register = restTemplate.exchange(
                "/api/v1/auth/register", HttpMethod.POST,
                new HttpEntity<>(new RegisterRequest(email, password), headers), TokenResponse.class);

        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("auth-integration-flow");
        TokenResponse firstPair = register.getBody();
        assertThat(firstPair).isNotNull();
        assertThat(firstPair.roles()).containsExactly(AccountRole.CUSTOMER);
        Jwt jwt = jwtDecoder.decode(firstPair.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(firstPair.accountId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");

        AuthAccount stored = accountRepository.findByEmail(email).orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, stored.getPasswordHash())).isTrue();

        ResponseEntity<TokenResponse> login = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email.toUpperCase(), password), TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<TokenResponse> refresh = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(firstPair.refreshToken()), TokenResponse.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse rotated = refresh.getBody();
        assertThat(rotated).isNotNull();
        assertThat(rotated.refreshToken()).isNotEqualTo(firstPair.refreshToken());

        ResponseEntity<ApiErrorResponse> reuse = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(firstPair.refreshToken()), ApiErrorResponse.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("INVALID_REFRESH_TOKEN");

        ResponseEntity<Void> logout = restTemplate.postForEntity(
                "/api/v1/auth/logout", new RefreshRequest(rotated.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<ApiErrorResponse> afterLogout = restTemplate.postForEntity(
                "/api/v1/auth/refresh", new RefreshRequest(rotated.refreshToken()), ApiErrorResponse.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsDuplicateWeakAndWrongCredentialsWithoutLeakingSensitiveData() {
        String email = "duplicate@example.com";
        String password = "correct horse battery staple";
        restTemplate.postForEntity("/api/v1/auth/register",
                new RegisterRequest(email, password), TokenResponse.class);

        ResponseEntity<ApiErrorResponse> duplicate = restTemplate.postForEntity(
                "/api/v1/auth/register", new RegisterRequest(email, password), ApiErrorResponse.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("EMAIL_ALREADY_REGISTERED");

        ResponseEntity<ApiErrorResponse> wrong = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(email, "wrong"), ApiErrorResponse.class);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).extracting(ApiErrorResponse::message)
                .isEqualTo("Email or password is invalid");

        ResponseEntity<ApiErrorResponse> weak = restTemplate.postForEntity(
                "/api/v1/auth/register", new RegisterRequest("new@example.com", "short"), ApiErrorResponse.class);
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(weak.getBody()).extracting(ApiErrorResponse::errorCode).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void publishesOnlyThePublicKeyAndUsesConsistentSecurityErrors() {
        ResponseEntity<Map<String, Object>> jwks = restTemplate.exchange(
                "/.well-known/jwks.json",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(jwks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jwks.getBody()).containsKey("keys");
        assertThat(jwks.getBody().toString()).doesNotContain("\"d\"");

        ResponseEntity<ApiErrorResponse> protectedRequest = restTemplate.getForEntity(
                "/api/v1/private-probe", ApiErrorResponse.class);
        assertThat(protectedRequest.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(protectedRequest.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("UNAUTHORIZED");
    }
}
