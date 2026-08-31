package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.domain.Payment;
import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.exception.PaymentConflictException;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.repository.OutboxEventRepository;
import com.example.ecommerce.payment.repository.PaymentRepository;
import com.example.ecommerce.payment.repository.ProcessedEventRepository;
import com.example.ecommerce.payment.service.PaymentPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentSagaTransactionService {

    public static final String CONSUMER_NAME = "payment-request-v1";

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaTransactionService.class);
    private static final List<String> TERMINAL_OUTCOMES = List.of(
            PaymentEventFactory.PAYMENT_COMPLETED,
            PaymentEventFactory.PAYMENT_FAILED
    );

    private final PaymentRepository paymentRepository;
    private final PaymentPersistenceService persistenceService;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventFactory eventFactory;

    public PaymentSagaTransactionService(
            PaymentRepository paymentRepository,
            PaymentPersistenceService persistenceService,
            ProcessedEventRepository processedEventRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentEventFactory eventFactory
    ) {
        this.paymentRepository = paymentRepository;
        this.persistenceService = persistenceService;
        this.processedEventRepository = processedEventRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.eventFactory = eventFactory;
    }

    @Transactional
    public PaymentSagaProcessingResult applyAndRecord(
            PaymentRequestedMessage message,
            UUID paymentId,
            ChargeResult chargeResult
    ) {
        Payment payment = lockAndValidate(message, paymentId);
        if (alreadyProcessed(message)) {
            return PaymentSagaProcessingResult.ALREADY_PROCESSED;
        }

        persistenceService.applyChargeResult(paymentId, chargeResult);
        boolean outcomeCreated = createOutcomeIfAbsent(payment, message.correlationId());
        recordProcessed(message);
        log.info("Finalized payment saga eventId={} paymentId={} orderId={} status={}",
                message.eventId(), payment.getId(), payment.getOrderId(), payment.getStatus());
        return outcomeCreated
                ? PaymentSagaProcessingResult.APPLIED
                : PaymentSagaProcessingResult.ALREADY_APPLIED;
    }

    @Transactional
    public PaymentSagaProcessingResult recordExistingOutcome(
            PaymentRequestedMessage message,
            UUID paymentId
    ) {
        Payment payment = lockAndValidate(message, paymentId);
        if (alreadyProcessed(message)) {
            return PaymentSagaProcessingResult.ALREADY_PROCESSED;
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            throw new IllegalStateException("pending payment has no terminal outcome to recover");
        }

        boolean outcomeCreated = createOutcomeIfAbsent(payment, message.correlationId());
        recordProcessed(message);
        log.info("Recovered payment saga outcome eventId={} paymentId={} orderId={} status={} created={}",
                message.eventId(), payment.getId(), payment.getOrderId(), payment.getStatus(), outcomeCreated);
        return outcomeCreated
                ? PaymentSagaProcessingResult.APPLIED
                : PaymentSagaProcessingResult.ALREADY_APPLIED;
    }

    private Payment lockAndValidate(PaymentRequestedMessage message, UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment disappeared during saga processing"));
        if (!payment.getOrderId().equals(message.orderId())
                || !payment.matches(message.amount(), message.currency())) {
            throw new PaymentConflictException(message.orderId());
        }
        return payment;
    }

    private boolean alreadyProcessed(PaymentRequestedMessage message) {
        boolean processed = processedEventRepository.existsById(message.eventId());
        if (processed) {
            log.info("Ignored duplicate payment command eventId={} orderId={}",
                    message.eventId(), message.orderId());
        }
        return processed;
    }

    private boolean createOutcomeIfAbsent(Payment payment, String correlationId) {
        boolean exists = outboxEventRepository.existsByAggregateIdAndEventTypeIn(
                payment.getOrderId(), TERMINAL_OUTCOMES);
        if (exists) {
            return false;
        }
        outboxEventRepository.save(eventFactory.outcome(payment, correlationId));
        return true;
    }

    private void recordProcessed(PaymentRequestedMessage message) {
        processedEventRepository.save(ProcessedEvent.create(message, CONSUMER_NAME, Instant.now()));
    }
}
