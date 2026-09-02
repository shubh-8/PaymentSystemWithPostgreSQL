package com.example.demo.repository;

import com.example.demo.model.Payment;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;


@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final ConcurrentHashMap<String, Payment> paymentsById =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Payment> paymentByIdempotencyKey =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Payment> paymentByProviderPaymentId =
            new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {

        paymentsById.put(payment.getId(), payment);

        if (payment.getProviderPaymentId() != null) {
            paymentByProviderPaymentId.put(
                    payment.getProviderPaymentId(),
                    payment
            );
        }

        return payment;
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return Optional.ofNullable(
                paymentsById.get(paymentId)
        );
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(
            String idempotencyKey) {

        return Optional.ofNullable(
                paymentByIdempotencyKey.get(idempotencyKey)
        );
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(
            String providerPaymentId) {

        return Optional.ofNullable(
                paymentByProviderPaymentId.get(providerPaymentId)
        );
    }

    @Override
    public Payment saveIfAbsent(
            String idempotencyKey,
            Payment payment) {

        Payment existing =
                paymentByIdempotencyKey.putIfAbsent(
                        idempotencyKey,
                        payment
                );

        if (existing != null) {
            return existing;
        }

        paymentsById.put(payment.getId(), payment);

        return payment;
    }
}