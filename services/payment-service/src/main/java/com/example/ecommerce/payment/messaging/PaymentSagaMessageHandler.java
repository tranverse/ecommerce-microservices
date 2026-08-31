package com.example.ecommerce.payment.messaging;

import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.exception.PaymentConflictException;
import com.example.ecommerce.payment.processor.ChargeRequest;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.PaymentProcessor;
import com.example.ecommerce.payment.repository.ProcessedEventRepository;
import com.example.ecommerce.payment.service.PaymentPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PaymentSagaMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaMessageHandler.class);

    private final PaymentPersistenceService persistenceService;
    private final PaymentProcessor processor;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentSagaTransactionService transactionService;

    public PaymentSagaMessageHandler(
            PaymentPersistenceService persistenceService,
            PaymentProcessor processor,
            ProcessedEventRepository processedEventRepository,
            PaymentSagaTransactionService transactionService
    ) {
        this.persistenceService = persistenceService;
        this.processor = processor;
        this.processedEventRepository = processedEventRepository;
        this.transactionService = transactionService;
    }

    public PaymentSagaProcessingResult handle(PaymentRequestedMessage message) {
        if (processedEventRepository.existsById(message.eventId())) {
            log.info("Ignored duplicate payment command before processor eventId={} orderId={}",
                    message.eventId(), message.orderId());
            return PaymentSagaProcessingResult.ALREADY_PROCESSED;
        }

        PaymentResponse payment = initialize(message);
        if (payment.status() != PaymentStatus.PENDING) {
            return transactionService.recordExistingOutcome(message, payment.id());
        }

        ChargeResult result = processor.charge(new ChargeRequest(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency()
        ));
        return transactionService.applyAndRecord(message, payment.id(), result);
    }

    private PaymentResponse initialize(PaymentRequestedMessage message) {
        try {
            return persistenceService.initialize(
                    message.orderId(), message.amount(), message.currency());
        } catch (DataIntegrityViolationException exception) {
            return persistenceService.findMatching(
                            message.orderId(), message.amount(), message.currency())
                    .orElseThrow(() -> exception);
        } catch (IllegalArgumentException exception) {
            throw new PaymentConflictException(message.orderId());
        }
    }
}
