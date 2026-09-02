package com.example.demo.service;

import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.PaymentResponse;
import com.example.demo.dto.PaymentWebhookRequest;
import com.example.demo.exception.IdempotencyConflictException;
import com.example.demo.exception.InvalidPaymentStateException;
import com.example.demo.exception.PaymentNotFoundException;
import com.example.demo.model.Payment;
import com.example.demo.model.PaymentMethod;
import com.example.demo.model.PaymentStatus;
import com.example.demo.model.ProviderResultType;
import com.example.demo.processor.PaymentProcessor;
import com.example.demo.provider.ProviderResult;
import com.example.demo.repository.PaymentRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

        private final PaymentRepository paymentRepository;

        private final Map<PaymentMethod, PaymentProcessor> processors;
        private final Set<String> processedWebhookEvents = ConcurrentHashMap.newKeySet();

        public PaymentService(
                        PaymentRepository paymentRepository,
                        List<PaymentProcessor> paymentProcessors) {

                this.paymentRepository = paymentRepository;
                
                this.processors = paymentProcessors.stream()
                                .collect(Collectors.toMap(
                                                processor -> processor.supportedMethod(),
                                                processor -> processor));
        }

        public PaymentResponse createPayment(
                        String idempotencyKey,
                        CreatePaymentRequest request) {

                Payment existingPayment = paymentRepository
                                .findByIdempotencyKey(idempotencyKey)
                                .orElse(null);

                if (existingPayment != null) {
                        validateSameRequest(existingPayment, request);

                        return new PaymentResponse(
                                        existingPayment.getId(),
                                        existingPayment.getStatus());
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
                                now);

                Payment savedPayment;

                try {
                        savedPayment = paymentRepository.saveAndFlush(newPayment);

                } catch (DataIntegrityViolationException ex) {

                        Payment concurrentPayment = paymentRepository
                                        .findByIdempotencyKey(idempotencyKey)
                                        .orElseThrow(() -> ex);

                        validateSameRequest(
                                        concurrentPayment,
                                        request);

                        return new PaymentResponse(
                                        concurrentPayment.getId(),
                                        concurrentPayment.getStatus());
                }

                PaymentProcessor processor = processors.get(savedPayment.getPaymentMethod());

                if (processor == null) {
                        throw new IllegalArgumentException(
                                        "Unsupported payment method: "
                                                        + savedPayment.getPaymentMethod());
                }

                ProviderResult providerResult = processor.process(savedPayment);
                handleProviderResult(
                                savedPayment,
                                providerResult);

                return new PaymentResponse(
                                savedPayment.getId(),
                                savedPayment.getStatus());
        }

        public Payment getPayment(String paymentId) {
                return paymentRepository
                                .findById(paymentId)
                                .orElseThrow(() -> new PaymentNotFoundException(
                                                "Payment not found: " + paymentId));
        }

        public void processWebhook(PaymentWebhookRequest request) {

                // Check if the webhook event has already been processed
                if (processedWebhookEvents.contains(request.getEventId())) {
                        return;
                }

                Payment payment = paymentRepository
                                .findByProviderPaymentId(
                                                request.getProviderPaymentId())
                                .orElseThrow(() -> new RuntimeException(
                                                "Payment not found for provider payment id"));

                switch (request.getStatus()) {

                        case SUCCESS ->
                                updatePaymentStatus(
                                                payment,
                                                PaymentStatus.SUCCESS);

                        case FAILED ->
                                updatePaymentStatus(
                                                payment,
                                                PaymentStatus.FAILED);
                        default -> throw new IllegalArgumentException("Unexpected value: " + request.getStatus());
                }

                paymentRepository.save(payment);

                processedWebhookEvents.add(request.getEventId());
        }

        private void updatePaymentStatus(
                        Payment payment,
                        PaymentStatus newStatus) {

                PaymentStatus currentStatus = payment.getStatus();

                // Duplicate webhook
                if (currentStatus == newStatus) {
                        return;
                }

                boolean validTransition = currentStatus == PaymentStatus.PROCESSING
                                &&
                                (newStatus == PaymentStatus.SUCCESS
                                                || newStatus == PaymentStatus.FAILED);

                if (!validTransition) {
                        throw new InvalidPaymentStateException(
                                        "Invalid payment state transition: "
                                                        + currentStatus
                                                        + " -> "
                                                        + newStatus);
                }

                payment.updateStatus(newStatus);
        }

        private void validateSameRequest(
                        Payment existingPayment,
                        CreatePaymentRequest request) {

                boolean sameRequest = existingPayment.getUserId()
                                .equals(request.getUserId())
                                &&
                                existingPayment.getMerchantId()
                                                .equals(request.getMerchantId())
                                &&
                                existingPayment.getAmount() == request.getAmount()
                                &&
                                existingPayment.getCurrency()
                                                .equals(request.getCurrency())
                                &&
                                existingPayment.getPaymentMethod() == request.getPaymentMethod();

                if (!sameRequest) {
                        throw new IdempotencyConflictException(
                                        "Idempotency key already used for a different request");
                }
        }

        private void handleProviderResult(
                        Payment payment,
                        ProviderResult result) {

                if (result.getProviderPaymentId() != null) {
                        payment.setProviderPaymentId(
                                        result.getProviderPaymentId());
                }

                if (result.getResultType() == ProviderResultType.ACCEPTED) {

                        payment.updateStatus(
                                        PaymentStatus.PROCESSING);

                } else if (result.getResultType() == ProviderResultType.DEFINITIVE_FAILURE) {

                        payment.updateStatus(
                                        PaymentStatus.FAILED);

                } else if (result.getResultType() == ProviderResultType.RETRYABLE_FAILURE) {

                        payment.updateStatus(
                                        PaymentStatus.PROCESSING);
                }

                paymentRepository.save(payment);
        }

}