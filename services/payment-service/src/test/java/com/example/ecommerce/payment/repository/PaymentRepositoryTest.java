package com.example.ecommerce.payment.repository;

import com.example.ecommerce.payment.TestcontainersConfiguration;
import com.example.ecommerce.payment.config.JpaAuditingConfiguration;
import com.example.ecommerce.payment.domain.Payment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class})
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndFindsAPaymentByOpaqueOrderId() {
        UUID orderId = UUID.randomUUID();
        Payment saved = repository.saveAndFlush(payment(orderId));
        entityManager.clear();

        Payment found = repository.findByOrderId(orderId).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getAmount()).isEqualByComparingTo("25.50");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void enforcesOnePaymentPerOrder() {
        UUID orderId = UUID.randomUUID();
        repository.saveAndFlush(payment(orderId));

        assertThatThrownBy(() -> repository.saveAndFlush(payment(orderId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsCompletedAndRefundedStateDataConsistently() {
        Payment payment = payment(UUID.randomUUID());
        payment.complete("charge-repository-001");
        repository.saveAndFlush(payment);

        payment.refund("refund-repository-001");
        Payment refunded = repository.saveAndFlush(payment);

        assertThat(refunded.getProviderReference()).isEqualTo("charge-repository-001");
        assertThat(refunded.getRefundReference()).isEqualTo("refund-repository-001");
    }

    private Payment payment(UUID orderId) {
        return Payment.create(orderId, new BigDecimal("25.50"), "USD");
    }
}
