package com.example.ecommerce.order.repository;

import com.example.ecommerce.order.TestcontainersConfiguration;
import com.example.ecommerce.order.config.JpaAuditingConfiguration;
import com.example.ecommerce.order.domain.CustomerOrder;
import com.example.ecommerce.order.domain.OrderItem;
import com.example.ecommerce.order.domain.ProductSnapshot;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class})
class CustomerOrderRepositoryTest {

    @Autowired
    private CustomerOrderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAggregateAndFetchesDetailsWithoutLazyQueriesDuringMapping() {
        UUID customerId = UUID.randomUUID();
        CustomerOrder saved = repository.saveAndFlush(order(customerId, "repository-key-001"));
        entityManager.clear();

        CustomerOrder found = repository.findByIdAndCustomerId(saved.getId(), customerId).orElseThrow();

        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getItems())
                .extracting(OrderItem::getProductSku)
                .containsExactly("SKU-001", "SKU-002");
        assertThat(found.getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void enforcesIdempotencyKeyUniquenessPerCustomer() {
        UUID customerId = UUID.randomUUID();
        repository.saveAndFlush(order(customerId, "repository-key-002"));

        assertThatThrownBy(() -> repository.saveAndFlush(order(customerId, "repository-key-002")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private CustomerOrder order(UUID customerId, String idempotencyKey) {
        return CustomerOrder.create(
                customerId,
                idempotencyKey,
                "b".repeat(64),
                List.of(
                        new ProductSnapshot(
                                UUID.randomUUID(),
                                "SKU-001",
                                "First product",
                                new BigDecimal("10.00"),
                                "USD",
                                1
                        ),
                        new ProductSnapshot(
                                UUID.randomUUID(),
                                "SKU-002",
                                "Second product",
                                new BigDecimal("7.50"),
                                "USD",
                                2
                        )
                )
        );
    }
}
