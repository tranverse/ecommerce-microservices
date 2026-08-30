package com.example.ecommerce.payment.service;

import com.example.ecommerce.payment.domain.Payment;
import com.example.ecommerce.payment.domain.PaymentFailureReason;
import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.exception.InvalidPaymentStateException;
import com.example.ecommerce.payment.exception.PaymentConflictException;
import com.example.ecommerce.payment.exception.PaymentNotFoundException;
import com.example.ecommerce.payment.exception.PaymentProcessorContractException;
import com.example.ecommerce.payment.mapper.PaymentMapper;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.RefundResult;
import com.example.ecommerce.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PaymentPersistenceService.class);

    private final PaymentRepository repository;
    private final PaymentMapper mapper;

    public PaymentPersistenceService(PaymentRepository repository, PaymentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public PaymentResponse initialize(UUID orderId, BigDecimal amount, String currency) {
        Optional<Payment> existing = repository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return matchingResponse(existing.get(), amount, currency);
        }
        Payment saved = repository.saveAndFlush(Payment.create(orderId, amount, currency));
        log.info("Initialized payment paymentId={} orderId={}", saved.getId(), orderId);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findMatching(UUID orderId, BigDecimal amount, String currency) {
        return repository.findByOrderId(orderId)
                .map(payment -> matchingResponse(payment, amount, currency));
    }

    @Transactional(readOnly = true)
    public PaymentResponse prepareRefund(UUID orderId) {
        Payment payment = findByOrderId(orderId);
        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException(payment.getStatus(), "refund");
        }
        return mapper.toResponse(payment);
    }

    @Transactional
    public PaymentResponse applyChargeResult(UUID paymentId, ChargeResult result) {
        Payment payment = findByIdForUpdate(paymentId);
        if (result.outcome() == ChargeResult.Outcome.APPROVED) {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                throw new PaymentProcessorContractException();
            }
            if ((payment.getStatus() == PaymentStatus.COMPLETED
                    || payment.getStatus() == PaymentStatus.REFUNDED)
                    && !Objects.equals(payment.getProviderReference(), result.providerReference())) {
                throw new PaymentProcessorContractException();
            }
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                return mapper.toResponse(payment);
            }
            payment.complete(result.providerReference());
        } else {
            if (payment.getStatus() == PaymentStatus.COMPLETED
                    || payment.getStatus() == PaymentStatus.REFUNDED) {
                throw new PaymentProcessorContractException();
            }
            payment.fail(PaymentFailureReason.DECLINED);
        }
        Payment saved = repository.saveAndFlush(payment);
        log.info("Applied payment outcome paymentId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getStatus());
        return mapper.toResponse(saved);
    }

    @Transactional
    public PaymentResponse applyRefundResult(UUID paymentId, RefundResult result) {
        Payment payment = findByIdForUpdate(paymentId);
        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException(payment.getStatus(), "refund");
        }
        payment.refund(result.providerReference());
        Payment saved = repository.saveAndFlush(payment);
        log.info("Refunded payment paymentId={} orderId={}", saved.getId(), saved.getOrderId());
        return mapper.toResponse(saved);
    }

    private PaymentResponse matchingResponse(Payment payment, BigDecimal amount, String currency) {
        if (!payment.matches(amount, currency)) {
            throw new PaymentConflictException(payment.getOrderId());
        }
        return mapper.toResponse(payment);
    }

    private Payment findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private Payment findByIdForUpdate(UUID paymentId) {
        return repository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment disappeared during processing"));
    }
}
