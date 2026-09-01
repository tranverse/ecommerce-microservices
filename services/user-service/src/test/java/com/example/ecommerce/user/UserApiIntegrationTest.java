package com.example.ecommerce.user;

import com.example.ecommerce.user.dto.AddressRequest;
import com.example.ecommerce.user.dto.AddressResponse;
import com.example.ecommerce.user.dto.ProfileResponse;
import com.example.ecommerce.user.dto.ProfileUpsertRequest;
import com.example.ecommerce.user.exception.ApiErrorResponse;
import com.example.ecommerce.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(
        properties = "management.prometheus.metrics.export.enabled=true",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import(TestcontainersConfiguration.class)
class UserApiIntegrationTest {

    private static final String USER_ONE_TOKEN = "user-one-token";
    private static final String USER_TWO_TOKEN = "user-two-token";
    private static final String GUEST_TOKEN = "guest-token";

    private final UUID userOne = UUID.randomUUID();
    private final UUID userTwo = UUID.randomUUID();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserProfileRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(jwtDecoder.decode(USER_ONE_TOKEN)).thenReturn(jwt(USER_ONE_TOKEN, userOne, "CUSTOMER"));
        when(jwtDecoder.decode(USER_TWO_TOKEN)).thenReturn(jwt(USER_TWO_TOKEN, userTwo, "CUSTOMER"));
        when(jwtDecoder.decode(GUEST_TOKEN)).thenReturn(jwt(GUEST_TOKEN, UUID.randomUUID(), "GUEST"));
    }

    @Test
    void managesAProfileAndAddressesUsingTheJwtSubject() {
        ResponseEntity<ProfileResponse> created = exchange(
                "/api/v1/users/me",
                HttpMethod.PUT,
                new ProfileUpsertRequest("Linh Nguyen", "+84901234567"),
                USER_ONE_TOKEN,
                ProfileResponse.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().id()).isEqualTo(userOne);

        ResponseEntity<AddressResponse> home = exchange(
                "/api/v1/users/me/addresses",
                HttpMethod.POST,
                address("HOME", "1 Nguyen Hue", true),
                USER_ONE_TOKEN,
                AddressResponse.class
        );
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AddressResponse> office = exchange(
                "/api/v1/users/me/addresses",
                HttpMethod.POST,
                address("OFFICE", "2 Le Loi", true),
                USER_ONE_TOKEN,
                AddressResponse.class
        );
        assertThat(office.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ProfileResponse afterSecondDefault = exchange(
                "/api/v1/users/me", HttpMethod.GET, null, USER_ONE_TOKEN, ProfileResponse.class
        ).getBody();
        assertThat(afterSecondDefault).isNotNull();
        assertThat(afterSecondDefault.addresses()).hasSize(2);
        assertThat(afterSecondDefault.addresses()).filteredOn(AddressResponse::defaultAddress)
                .extracting(AddressResponse::id)
                .containsExactly(office.getBody().id());

        ResponseEntity<AddressResponse> switched = exchange(
                "/api/v1/users/me/addresses/" + home.getBody().id(),
                HttpMethod.PUT,
                address("HOME", "1 Nguyen Hue", true),
                USER_ONE_TOKEN,
                AddressResponse.class
        );
        assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(switched.getBody()).extracting(AddressResponse::defaultAddress).isEqualTo(true);
    }

    @Test
    void exposesPrometheusMetricsWithoutAuthentication() {
        ResponseEntity<String> metrics = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metrics.getBody()).contains("jvm_memory_used_bytes");
    }

    @Test
    void preventsCrossUserAddressAccess() {
        exchange("/api/v1/users/me", HttpMethod.PUT,
                new ProfileUpsertRequest("User One", null), USER_ONE_TOKEN, ProfileResponse.class);
        AddressResponse address = exchange(
                "/api/v1/users/me/addresses", HttpMethod.POST,
                address("HOME", "1 Nguyen Hue", false), USER_ONE_TOKEN, AddressResponse.class
        ).getBody();

        exchange("/api/v1/users/me", HttpMethod.PUT,
                new ProfileUpsertRequest("User Two", null), USER_TWO_TOKEN, ProfileResponse.class);
        ResponseEntity<ApiErrorResponse> forbiddenByOwnership = exchange(
                "/api/v1/users/me/addresses/" + address.id(),
                HttpMethod.DELETE,
                null,
                USER_TWO_TOKEN,
                ApiErrorResponse.class
        );

        assertThat(forbiddenByOwnership.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forbiddenByOwnership.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("ADDRESS_NOT_FOUND");
    }

    @Test
    void enforcesAuthenticationRolesAndValidation() {
        ResponseEntity<ApiErrorResponse> unauthenticated = restTemplate.getForEntity(
                "/api/v1/users/me", ApiErrorResponse.class);
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("UNAUTHORIZED");

        ResponseEntity<ApiErrorResponse> wrongRole = exchange(
                "/api/v1/users/me", HttpMethod.GET, null, GUEST_TOKEN, ApiErrorResponse.class);
        assertThat(wrongRole.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongRole.getBody()).extracting(ApiErrorResponse::errorCode).isEqualTo("FORBIDDEN");

        ResponseEntity<ApiErrorResponse> invalid = exchange(
                "/api/v1/users/me",
                HttpMethod.PUT,
                new ProfileUpsertRequest("", "0901234567"),
                USER_ONE_TOKEN,
                ApiErrorResponse.class
        );
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("VALIDATION_ERROR");
    }

    private <T> ResponseEntity<T> exchange(
            String path,
            HttpMethod method,
            Object body,
            String token,
            Class<T> responseType
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "user-integration-flow");
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), responseType);
    }

    private Jwt jwt(String token, UUID subject, String role) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plus(15, ChronoUnit.MINUTES))
                .claim("roles", List.of(role))
                .build();
    }

    private AddressRequest address(String label, String line1, boolean defaultAddress) {
        return new AddressRequest(
                label,
                "Linh Nguyen",
                line1,
                null,
                "Ho Chi Minh City",
                null,
                "700000",
                "VN",
                "+84901234567",
                defaultAddress
        );
    }
}
