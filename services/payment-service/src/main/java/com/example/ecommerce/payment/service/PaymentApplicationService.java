package com.example.ecommerce.payment.service;

import com.example.ecommerce.payment.domain.PaymentStatus;
import com.example.ecommerce.payment.dto.PaymentResponse;
import com.example.ecommerce.payment.dto.ProcessPaymentCommand;
import com.example.ecommerce.payment.processor.ChargeRequest;
import com.example.ecommerce.payment.processor.ChargeResult;
import com.example.ecommerce.payment.processor.PaymentProcessor;
import com.example.ecommerce.payment.processor.RefundRequest;
import com.example.ecommerce.payment.processor.RefundResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentApplicationService {

    private final PaymentPersistenceService persistenceService;
    private final PaymentProcessor processor;

    public PaymentApplicationService(
            PaymentPersistenceService persistenceService,
            PaymentProcessor processor
    ) {
        this.persistenceService = persistenceService;
        this.processor = processor;
    }

    public PaymentResponse process(ProcessPaymentCommand command) {
        PaymentResponse payment = initialize(command);
        if (payment.status() != PaymentStatus.PENDING) {
            return payment;
        }

        ChargeResult result = processor.charge(new ChargeRequest(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency()
        ));
        return persistenceService.applyChargeResult(payment.id(), result);
    }

    public PaymentResponse refund(UUID orderId) {
        PaymentResponse payment = persistenceService.prepareRefund(orderId);
        if (payment.status() == PaymentStatus.REFUNDED) {
            return payment;
        }

        RefundResult result = processor.refund(new RefundRequest(
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.currency(),
                payment.providerReference()
        ));
        return persistenceService.applyRefundResult(payment.id(), result);
    }

    private PaymentResponse initialize(ProcessPaymentCommand command) {
        try {
            return persistenceService.initialize(command.orderId(), command.amount(), command.currency());
        } catch (DataIntegrityViolationException exception) {
            return persistenceService.findMatching(command.orderId(), command.amount(), command.currency())
                    .orElseThrow(() -> exception);
        }
    }
}
