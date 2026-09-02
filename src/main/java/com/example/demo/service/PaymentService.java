package com.example.demo.service;

import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.PaymentResponse;
import com.example.demo.dto.PaymentWebhookRequest;
import com.example.demo.model.Payment;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.ProviderResultType;
import com.example.demo.provider.PaymentProvider;
import com.example.demo.provider.ProviderResult;
import com.example.demo.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Set<String> processedWebhookEvents =
        ConcurrentHashMap.newKeySet();

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    public PaymentResponse createPayment(
            String idempotencyKey,
            CreatePaymentRequest request) {

        Payment existingPayment =
                paymentRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElse(null);

        if (existingPayment != null) {
            validateSameRequest(existingPayment, request);

            return new PaymentResponse(
                    existingPayment.getId(),
                    existingPayment.getStatus()
            );
        }

        Instant now = Instant.now();

        Payment newPayment = new Payment(
                "pay_" + UUID.randomUUID(),
                request.getUserId(),
                request.getMerchantId(),
                request.getAmount(),
                request.getCurrency(),
                request.getPaymentMethod(),
                idempotencyKey,
                PaymentStatus.CREATED,
                null,
                0,
                now,
                now
        );

        Payment savedPayment =
                paymentRepository.saveIfAbsent(
                        idempotencyKey,
                        newPayment
                );

        // Another concurrent request may have won.
        if (!savedPayment.getId().equals(newPayment.getId())) {

            validateSameRequest(savedPayment, request);

            return new PaymentResponse(
                    savedPayment.getId(),
                    savedPayment.getStatus()
            );
        }

        ProviderResult providerResult =
                paymentProvider.initiatePayment(savedPayment);

        handleProviderResult(
                savedPayment,
                providerResult
        );

        return new PaymentResponse(
                savedPayment.getId(),
                savedPayment.getStatus()
        );
    }

    public Payment getPayment(String paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() ->
                        new RuntimeException("Payment not found")
                );
    }

    public void processWebhook(PaymentWebhookRequest request) {

        if (processedWebhookEvents.contains(request.getEventId())) {
            return;
        }

        Payment payment =
                paymentRepository
                        .findByProviderPaymentId(
                                request.getProviderPaymentId()
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Payment not found for provider payment id"
                                )
                        );

        switch (request.getStatus()) {

            case SUCCESS ->
                    updatePaymentStatus(
                            payment,
                            PaymentStatus.SUCCESS
                    );

            case FAILED ->
                    updatePaymentStatus(
                            payment,
                            PaymentStatus.FAILED
                    );
        }

        paymentRepository.save(payment);

        processedWebhookEvents.add(request.getEventId());
    }

    private void updatePaymentStatus(
        Payment payment,
        PaymentStatus newStatus) {

        PaymentStatus currentStatus =
                payment.getStatus();

        // Duplicate webhook
        if (currentStatus == newStatus) {
            return;
        }

        boolean validTransition =
                currentStatus == PaymentStatus.PROCESSING
                        &&
                (newStatus == PaymentStatus.SUCCESS
                        || newStatus == PaymentStatus.FAILED);

        if (!validTransition) {
            throw new IllegalStateException(
                    "Invalid payment state transition: "
                            + currentStatus
                            + " -> "
                            + newStatus
            );
        }

        payment.setStatus(newStatus);
    }

    private void validateSameRequest(
        Payment existingPayment,
        CreatePaymentRequest request) {

        boolean sameRequest =
                existingPayment.getUserId()
                        .equals(request.getUserId())
                &&
                existingPayment.getMerchantId()
                        .equals(request.getMerchantId())
                &&
                existingPayment.getAmount()
                        == request.getAmount()
                &&
                existingPayment.getCurrency()
                        .equals(request.getCurrency())
                &&
                existingPayment.getPaymentMethod()
                        == request.getPaymentMethod();

        if (!sameRequest) {
            throw new IllegalArgumentException(
                    "Idempotency key already used for a different request"
            );
        }
    }

    private void handleProviderResult(
        Payment payment,
        ProviderResult result) {

        if (result.getProviderPaymentId() != null) {
            payment.setProviderPaymentId(
                    result.getProviderPaymentId()
            );
        }

        if (result.getResultType()
                == ProviderResultType.ACCEPTED) {

            payment.setStatus(
                    PaymentStatus.PROCESSING
            );

        } else if (result.getResultType()
                == ProviderResultType.DEFINITIVE_FAILURE) {

            payment.setStatus(
                    PaymentStatus.FAILED
            );

        } else if (result.getResultType()
                == ProviderResultType.RETRYABLE_FAILURE) {

            payment.setStatus(
                    PaymentStatus.PROCESSING
            );
        }

        paymentRepository.save(payment);
    }

}