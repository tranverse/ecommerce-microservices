package com.example.ecommerce.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final AtomicInteger DOWNSTREAM_REQUESTS = new AtomicInteger();
    private static final DisposableServer DOWNSTREAM = HttpServer.create()
            .port(0)
            .handle((request, response) -> {
                DOWNSTREAM_REQUESTS.incrementAndGet();
                String correlationId = request.requestHeaders().get("X-Correlation-ID");
                boolean authorizationPresent = request.requestHeaders().contains(HttpHeaders.AUTHORIZATION);
                boolean spoofedIdentityPresent = request.requestHeaders().contains("X-User-Id")
                        || request.requestHeaders().contains("X-User-Roles");
                String body = """
                        {"path":"%s","method":"%s","correlationId":"%s",\
                        "authorizationPresent":%s,"spoofedIdentityPresent":%s}
                        """.formatted(
                        request.uri().split("\\?", 2)[0],
                        request.method().name(),
                        correlationId,
                        authorizationPresent,
                        spoofedIdentityPresent
                );
                return response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .header("X-Correlation-ID", correlationId)
                        .sendString(Mono.just(body));
            })
            .bindNow();

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        String baseUrl = "http://localhost:" + DOWNSTREAM.port();
        registry.add("AUTH_SERVICE_URL", () -> baseUrl);
        registry.add("USER_SERVICE_URL", () -> baseUrl);
        registry.add("PRODUCT_SERVICE_URL", () -> baseUrl);
        registry.add("INVENTORY_SERVICE_URL", () -> baseUrl);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.disposeNow();
    }

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @BeforeEach
    void setUpDecoder() {
        DOWNSTREAM_REQUESTS.set(0);
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            return switch (token) {
                case "customer-token" -> Mono.just(jwt(token, "CUSTOMER"));
                case "admin-token" -> Mono.just(jwt(token, "ADMIN"));
                case "guest-token" -> Mono.just(jwt(token, "GUEST"));
                default -> Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("invalid token"));
            };
        });
    }

    @Test
    void routesPublicAuthAndProductReadsWithoutAToken() {
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .header("X-Correlation-ID", "gateway-public-auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\",\"password\":\"not-forwarded-to-logs\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values("X-Correlation-ID", values ->
                        assertThat(values).containsExactly("gateway-public-auth"))
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/auth/login")
                .jsonPath("$.correlationId").isEqualTo("gateway-public-auth");

        webTestClient.get()
                .uri("/api/v1/products/search?q=laptop")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/products/search");

        assertThat(DOWNSTREAM_REQUESTS).hasValue(2);
    }

    @Test
    void rejectsProtectedRequestsBeforeTheyReachDownstream() {
        webTestClient.get()
                .uri("/api/v1/users/me")
                .header("X-Correlation-ID", "gateway-unauthorized")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("X-Correlation-ID", "gateway-unauthorized")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.correlationId").isEqualTo("gateway-unauthorized");

        webTestClient.get()
                .uri("/api/v1/users/me")
                .headers(headers -> headers.setBearerAuth("guest-token"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("FORBIDDEN");

        assertThat(DOWNSTREAM_REQUESTS).hasValue(0);
    }

    @Test
    void relaysTheBearerTokenAndRemovesSpoofedIdentityHeaders() {
        webTestClient.get()
                .uri("/api/v1/users/me")
                .headers(headers -> {
                    headers.setBearerAuth("customer-token");
                    headers.set("X-User-Id", UUID.randomUUID().toString());
                    headers.set("X-User-Roles", "ADMIN");
                    headers.set("X-Correlation-ID", "gateway-user-route");
                })
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authorizationPresent").isEqualTo(true)
                .jsonPath("$.spoofedIdentityPresent").isEqualTo(false)
                .jsonPath("$.correlationId").isEqualTo("gateway-user-route");
    }

    @Test
    void appliesCoarseAdminAuthorizationToManagementRoutes() {
        webTestClient.post()
                .uri("/api/v1/products")
                .headers(headers -> headers.setBearerAuth("customer-token"))
                .exchange()
                .expectStatus().isForbidden();

        webTestClient.put()
                .uri("/api/v1/inventory/items/" + UUID.randomUUID())
                .headers(headers -> headers.setBearerAuth("admin-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"totalQuantity\":10}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.method").isEqualTo("PUT");

        assertThat(DOWNSTREAM_REQUESTS).hasValue(1);
    }

    @Test
    void rejectsAnInvalidTokenAndRegeneratesAnUnsafeCorrelationId() {
        webTestClient.get()
                .uri("/api/v1/users/me")
                .headers(headers -> {
                    headers.setBearerAuth("invalid-token");
                    headers.set("X-Correlation-ID", "../../ unsafe");
                })
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().value("X-Correlation-ID", value ->
                        assertThat(UUID.fromString(value)).isNotNull())
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("UNAUTHORIZED");

        assertThat(DOWNSTREAM_REQUESTS).hasValue(0);
    }

    private Jwt jwt(String token, String role) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .claim("roles", List.of(role))
                .build();
    }
}
