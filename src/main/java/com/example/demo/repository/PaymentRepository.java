package com.example.demo.repository;

import com.example.demo.model.Payment;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(String paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Payment saveIfAbsent(String idempotencyKey, Payment payment);

}