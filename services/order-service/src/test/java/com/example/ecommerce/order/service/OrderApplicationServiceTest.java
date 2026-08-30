package com.example.ecommerce.order.service;

import com.example.ecommerce.order.client.CatalogProductResponse;
import com.example.ecommerce.order.client.ProductCatalogClient;
import com.example.ecommerce.order.domain.OrderStatus;
import com.example.ecommerce.order.domain.ProductSnapshot;
import com.example.ecommerce.order.dto.CreateOrderItemRequest;
import com.example.ecommerce.order.dto.CreateOrderRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.exception.InvalidOrderItemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String IDEMPOTENCY_KEY = "checkout-test-001";

    @Mock
    private ProductCatalogClient productCatalogClient;

    @Mock
    private OrderPersistenceService persistenceService;

    @InjectMocks
    private OrderApplicationService service;

    @Test
    void createsOrderFromTrustedCatalogSnapshotsInCanonicalOrder() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CreateOrderRequest request = request(
                new CreateOrderItemRequest(secondId, 3),
                new CreateOrderItemRequest(firstId, 2)
        );
        when(persistenceService.findIdempotentOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString()))
                .thenReturn(Optional.empty());
        when(productCatalogClient.getProducts(Set.of(firstId, secondId))).thenReturn(List.of(
                product(secondId, "SKU-002", "5.50", "USD", "ACTIVE"),
                product(firstId, "SKU-001", "10.25", "USD", "ACTIVE")
        ));
        when(persistenceService.createOrder(
                eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString(), anyList()
        )).thenReturn(response(UUID.randomUUID()));

        service.createOrder(CUSTOMER_ID, IDEMPOTENCY_KEY, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSnapshot>> snapshotsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(persistenceService).createOrder(
                eq(CUSTOMER_ID),
                eq(IDEMPOTENCY_KEY),
                hashCaptor.capture(),
                snapshotsCaptor.capture()
        );
        assertThat(hashCaptor.getValue()).matches("[a-f0-9]{64}");
        assertThat(snapshotsCaptor.getValue())
                .extracting(ProductSnapshot::productId)
                .containsExactly(firstId, secondId);
        assertThat(snapshotsCaptor.getValue().getFirst().unitPrice()).isEqualByComparingTo("10.25");
        assertThat(snapshotsCaptor.getValue().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void returnsExistingIdempotentOrderWithoutCallingCatalog() {
        OrderResponse existing = response(UUID.randomUUID());
        when(persistenceService.findIdempotentOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString()))
                .thenReturn(Optional.of(existing));

        OrderResponse result = service.createOrder(
                CUSTOMER_ID,
                IDEMPOTENCY_KEY,
                request(new CreateOrderItemRequest(UUID.randomUUID(), 1))
        );

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(productCatalogClient);
        verify(persistenceService, never()).createOrder(any(), anyString(), anyString(), anyList());
    }

    @Test
    void rejectsDuplicateProductsBeforeCallingCatalog() {
        UUID productId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createOrder(
                CUSTOMER_ID,
                IDEMPOTENCY_KEY,
                request(
                        new CreateOrderItemRequest(productId, 1),
                        new CreateOrderItemRequest(productId, 2)
                )
        )).isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("duplicate");

        verifyNoInteractions(productCatalogClient, persistenceService);
    }

    @Test
    void rejectsMissingAndInactiveProducts() {
        UUID activeId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        CreateOrderRequest missingRequest = request(
                new CreateOrderItemRequest(activeId, 1),
                new CreateOrderItemRequest(missingId, 1)
        );
        when(persistenceService.findIdempotentOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString()))
                .thenReturn(Optional.empty());
        when(productCatalogClient.getProducts(Set.of(activeId, missingId)))
                .thenReturn(List.of(product(activeId, "SKU-001", "10.00", "USD", "ACTIVE")));

        assertThatThrownBy(() -> service.createOrder(CUSTOMER_ID, IDEMPOTENCY_KEY, missingRequest))
                .isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("not found");

        UUID inactiveId = UUID.randomUUID();
        CreateOrderRequest inactiveRequest = request(new CreateOrderItemRequest(inactiveId, 1));
        when(productCatalogClient.getProducts(Set.of(inactiveId)))
                .thenReturn(List.of(product(inactiveId, "SKU-002", "9.00", "USD", "INACTIVE")));

        assertThatThrownBy(() -> service.createOrder(CUSTOMER_ID, IDEMPOTENCY_KEY, inactiveRequest))
                .isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void rejectsMixedCurrencies() {
        UUID usdId = UUID.randomUUID();
        UUID eurId = UUID.randomUUID();
        CreateOrderRequest request = request(
                new CreateOrderItemRequest(usdId, 1),
                new CreateOrderItemRequest(eurId, 1)
        );
        when(persistenceService.findIdempotentOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString()))
                .thenReturn(Optional.empty());
        when(productCatalogClient.getProducts(Set.of(usdId, eurId))).thenReturn(List.of(
                product(usdId, "SKU-USD", "10.00", "USD", "ACTIVE"),
                product(eurId, "SKU-EUR", "9.00", "EUR", "ACTIVE")
        ));

        assertThatThrownBy(() -> service.createOrder(CUSTOMER_ID, IDEMPOTENCY_KEY, request))
                .isInstanceOf(InvalidOrderItemException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void recoversConcurrentIdempotencyInsertRace() {
        UUID productId = UUID.randomUUID();
        OrderResponse winner = response(UUID.randomUUID());
        when(persistenceService.findIdempotentOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString()))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(productCatalogClient.getProducts(Set.of(productId)))
                .thenReturn(List.of(product(productId, "SKU-001", "10.00", "USD", "ACTIVE")));
        when(persistenceService.createOrder(eq(CUSTOMER_ID), eq(IDEMPOTENCY_KEY), anyString(), anyList()))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        OrderResponse result = service.createOrder(
                CUSTOMER_ID,
                IDEMPOTENCY_KEY,
                request(new CreateOrderItemRequest(productId, 1))
        );

        assertThat(result).isSameAs(winner);
    }

    private CreateOrderRequest request(CreateOrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    private CatalogProductResponse product(
            UUID id,
            String sku,
            String price,
            String currency,
            String status
    ) {
        return new CatalogProductResponse(
                id,
                sku,
                "Product " + sku,
                new BigDecimal(price),
                currency,
                status
        );
    }

    private OrderResponse response(UUID id) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new OrderResponse(
                id,
                CUSTOMER_ID,
                OrderStatus.PENDING,
                null,
                "USD",
                new BigDecimal("10.00"),
                List.of(),
                0,
                now,
                now
        );
    }
}
