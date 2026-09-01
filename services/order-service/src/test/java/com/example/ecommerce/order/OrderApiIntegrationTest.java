package com.example.ecommerce.order;

import com.example.ecommerce.order.client.CatalogProductResponse;
import com.example.ecommerce.order.client.ProductCatalogClient;
import com.example.ecommerce.order.dto.CreateOrderItemRequest;
import com.example.ecommerce.order.dto.CreateOrderRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.PageResponse;
import com.example.ecommerce.order.exception.ApiErrorResponse;
import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
import com.example.ecommerce.order.repository.CustomerOrderRepository;
import com.example.ecommerce.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderApiIntegrationTest {

    private static final String USER_ONE_TOKEN = "order-user-one-token";
    private static final String USER_TWO_TOKEN = "order-user-two-token";
    private static final String GUEST_TOKEN = "order-guest-token";

    private final UUID userOne = UUID.randomUUID();
    private final UUID userTwo = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CustomerOrderRepository repository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        repository.deleteAll();
        reset(jwtDecoder, productCatalogClient);
        when(jwtDecoder.decode(USER_ONE_TOKEN)).thenReturn(jwt(USER_ONE_TOKEN, userOne, "CUSTOMER"));
        when(jwtDecoder.decode(USER_TWO_TOKEN)).thenReturn(jwt(USER_TWO_TOKEN, userTwo, "CUSTOMER"));
        when(jwtDecoder.decode(GUEST_TOKEN)).thenReturn(jwt(GUEST_TOKEN, UUID.randomUUID(), "GUEST"));
        when(productCatalogClient.getProducts(Set.of(productId))).thenReturn(List.of(
                new CatalogProductResponse(
                        productId,
                        "SKU-TRUSTED-001",
                        "Trusted Product Name",
                        new BigDecimal("19.99"),
                        "USD",
                        "ACTIVE"
                )
        ));
    }

    @Test
    void createsAnIdempotentOrderFromTrustedProductDataAndEnforcesOwnership() {
        CreateOrderRequest request = new CreateOrderRequest(List.of(new CreateOrderItemRequest(productId, 2)));

        ResponseEntity<OrderResponse> created = exchange(
                "/api/v1/orders", HttpMethod.POST, request, USER_ONE_TOKEN,
                "checkout-integration-001", OrderResponse.class
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().customerId()).isEqualTo(userOne);
        assertThat(created.getBody().totalAmount()).isEqualByComparingTo("39.98");
        assertThat(created.getBody().items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Trusted Product Name");
            assertThat(item.productSku()).isEqualTo("SKU-TRUSTED-001");
            assertThat(item.unitPrice()).isEqualByComparingTo("19.99");
        });

        ResponseEntity<OrderResponse> replayed = exchange(
                "/api/v1/orders", HttpMethod.POST, request, USER_ONE_TOKEN,
                "checkout-integration-001", OrderResponse.class
        );
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(replayed.getBody()).isNotNull();
        assertThat(replayed.getBody().id()).isEqualTo(created.getBody().id());
        verify(productCatalogClient, times(1)).getProducts(Set.of(productId));

        assertThat(outboxRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(created.getBody().id());
            assertThat(event.getTopic()).isEqualTo("inventory.commands.v1");
            assertThat(event.getEventType()).isEqualTo("InventoryReservationRequested");
            assertThat(event.getEventVersion()).isEqualTo(1);
            assertThat(event.getEventKey()).isEqualTo(created.getBody().id().toString());
            assertThat(event.getCorrelationId()).isEqualTo("order-integration-flow");
            JsonNode envelope = event.getPayload();
            assertThat(envelope.path("eventId").asText()).isEqualTo(event.getId().toString());
            assertThat(envelope.path("payload").path("orderId").asText())
                    .isEqualTo(created.getBody().id().toString());
            assertThat(envelope.path("payload").path("items").get(0).path("quantity").asInt())
                    .isEqualTo(2);
        });

        ResponseEntity<ApiErrorResponse> conflictingReplay = exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new CreateOrderRequest(List.of(new CreateOrderItemRequest(productId, 3))),
                USER_ONE_TOKEN,
                "checkout-integration-001",
                ApiErrorResponse.class
        );
        assertThat(conflictingReplay.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflictingReplay.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        verify(productCatalogClient, times(1)).getProducts(Set.of(productId));

        UUID orderId = created.getBody().id();
        ResponseEntity<ApiErrorResponse> hiddenFromAnotherUser = exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                null,
                USER_TWO_TOKEN,
                null,
                ApiErrorResponse.class
        );
        assertThat(hiddenFromAnotherUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(hiddenFromAnotherUser.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("ORDER_NOT_FOUND");

        ResponseEntity<PageResponse> ownOrders = exchange(
                "/api/v1/orders?page=0&size=10",
                HttpMethod.GET,
                null,
                USER_ONE_TOKEN,
                null,
                PageResponse.class
        );
        assertThat(ownOrders.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownOrders.getBody()).isNotNull();
        assertThat(ownOrders.getBody().totalElements()).isEqualTo(1);
    }

    @Test
    void enforcesAuthenticationRolesAndRequestValidation() {
        ResponseEntity<ApiErrorResponse> unauthenticated = restTemplate.getForEntity(
                "/api/v1/orders", ApiErrorResponse.class
        );
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("UNAUTHORIZED");

        ResponseEntity<ApiErrorResponse> wrongRole = exchange(
                "/api/v1/orders", HttpMethod.GET, null, GUEST_TOKEN, null, ApiErrorResponse.class
        );
        assertThat(wrongRole.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongRole.getBody()).extracting(ApiErrorResponse::errorCode).isEqualTo("FORBIDDEN");

        ResponseEntity<ApiErrorResponse> invalid = exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new CreateOrderRequest(List.of()),
                USER_ONE_TOKEN,
                "checkout-integration-002",
                ApiErrorResponse.class
        );
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void returnsServiceUnavailableWithoutPersistingWhenProductCatalogIsUnavailable() {
        when(productCatalogClient.getProducts(Set.of(productId)))
                .thenThrow(new ProductCatalogUnavailableException());

        ResponseEntity<ApiErrorResponse> response = exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                new CreateOrderRequest(List.of(new CreateOrderItemRequest(productId, 1))),
                USER_ONE_TOKEN,
                "checkout-catalog-unavailable-001",
                ApiErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).extracting(ApiErrorResponse::errorCode)
                .isEqualTo("PRODUCT_CATALOG_UNAVAILABLE");
        assertThat(repository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    private <T> ResponseEntity<T> exchange(
            String path,
            HttpMethod method,
            Object body,
            String token,
            String idempotencyKey,
            Class<T> responseType
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "order-integration-flow");
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
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

}
