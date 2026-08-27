package com.example.ecommerce.user.controller;

import com.example.ecommerce.user.config.SecurityConfiguration;
import com.example.ecommerce.user.config.UserAuthProperties;
import com.example.ecommerce.user.domain.ProfileStatus;
import com.example.ecommerce.user.dto.ProfileResponse;
import com.example.ecommerce.user.security.SecurityErrorHandler;
import com.example.ecommerce.user.service.UserProfileService;
import com.example.ecommerce.user.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, CorrelationIdFilter.class})
@EnableConfigurationProperties(UserAuthProperties.class)
@TestPropertySource(properties = {
        "user.auth.issuer=http://localhost:8081",
        "user.auth.jwks-uri=http://localhost:8081/.well-known/jwks.json",
        "user.auth.connect-timeout=PT2S",
        "user.auth.read-timeout=PT2S"
})
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService service;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsATokenWithoutAnAllowedRole() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void upsertsTheAuthenticatedUsersProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.upsertProfile(eq(userId), any())).thenReturn(response(userId));

        mockMvc.perform(put("/api/v1/users/me")
                        .with(customer(userId.toString()))
                        .header("X-Correlation-ID", "user-controller-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Linh Nguyen",
                                  "phone": "+84901234567"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "user-controller-test"))
                .andExpect(jsonPath("$.id").value(userId.toString()));

        verify(service).upsertProfile(eq(userId), any());
    }

    @Test
    void rejectsAMalformedAuthenticatedSubject() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .with(customer("not-a-uuid"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Linh Nguyen"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_IDENTITY"));
    }

    @Test
    void returnsAStableValidationError() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .with(customer(UUID.randomUUID().toString()))
                        .header("X-Correlation-ID", "user-validation-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"","phone":"0901234567"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").value("user-validation-test"))
                .andExpect(jsonPath("$.details").isArray());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private ProfileResponse response(UUID userId) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ProfileResponse(
                userId,
                "Linh Nguyen",
                "+84901234567",
                ProfileStatus.ACTIVE,
                List.of(),
                0,
                now,
                now
        );
    }
}
