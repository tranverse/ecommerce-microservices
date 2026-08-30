package com.example.ecommerce.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String REQUEST_HASH = "a".repeat(64);

    @Test
    void createsPendingOrderFromImmutableProductSnapshots() {
        CustomerOrder order = CustomerOrder.create(
                CUSTOMER_ID,
                "checkout-request-001",
                REQUEST_HASH,
                List.of(
                        snapshot("SKU-001", "10.25", 2, "USD"),
                        snapshot("SKU-002", "5.50", 3, "USD")
                )
        );

        assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("37.00");
        assertThat(order.getItems())
                .extracting(OrderItem::getLineNumber)
                .containsExactly(1, 2);
        assertThat(order.getItems()).isUnmodifiable();
    }

    @Test
    void rejectsDuplicateProductsAndMixedCurrencies() {
        UUID duplicateId = UUID.randomUUID();
        ProductSnapshot first = snapshot(duplicateId, "SKU-001", "10.00", 1, "USD");
        ProductSnapshot duplicate = snapshot(duplicateId, "SKU-001", "10.00", 2, "USD");

        assertThatThrownBy(() -> CustomerOrder.create(
                CUSTOMER_ID,
                "checkout-request-002",
                REQUEST_HASH,
                List.of(first, duplicate)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");

        assertThatThrownBy(() -> CustomerOrder.create(
                CUSTOMER_ID,
                "checkout-request-003",
                REQUEST_HASH,
                List.of(first, snapshot("SKU-002", "9.00", 1, "EUR"))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void advancesOnlyThroughTheValidSuccessPathAndIgnoresDuplicateTransitions() {
        CustomerOrder order = order();

        assertThat(order.markInventoryReserved()).isTrue();
        assertThat(order.markInventoryReserved()).isFalse();
        assertThat(order.markPaymentPending()).isTrue();
        assertThat(order.markPaymentPending()).isFalse();
        assertThat(order.confirm()).isTrue();
        assertThat(order.confirm()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> order.cancel(OrderFailureReason.PAYMENT_FAILED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed");
    }

    @Test
    void rejectsOutOfOrderTransitionsAndMakesCancellationIdempotent() {
        CustomerOrder order = order();

        assertThatThrownBy(order::markPaymentPending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");

        assertThat(order.cancel(OrderFailureReason.INSUFFICIENT_INVENTORY)).isTrue();
        assertThat(order.cancel(OrderFailureReason.INSUFFICIENT_INVENTORY)).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo(OrderFailureReason.INSUFFICIENT_INVENTORY);

        assertThatThrownBy(() -> order.cancel(OrderFailureReason.SYSTEM_ERROR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different failure reason");
    }

    private CustomerOrder order() {
        return CustomerOrder.create(
                CUSTOMER_ID,
                "checkout-request-004",
                REQUEST_HASH,
                List.of(snapshot("SKU-001", "10.00", 1, "USD"))
        );
    }

    private ProductSnapshot snapshot(String sku, String price, int quantity, String currency) {
        return snapshot(UUID.randomUUID(), sku, price, quantity, currency);
    }

    private ProductSnapshot snapshot(UUID productId, String sku, String price, int quantity, String currency) {
        return new ProductSnapshot(
                productId,
                sku,
                "Product " + sku,
                new BigDecimal(price),
                currency,
                quantity
        );
    }
}
