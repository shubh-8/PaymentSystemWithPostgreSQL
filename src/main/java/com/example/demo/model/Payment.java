package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_provider_payment_id",
                        columnNames = "provider_payment_id"
                )
        }
)
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String merchantId;

    // Amount in smallest currency unit (paise for INR)
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Payment(
            String id,
            String userId,
            String merchantId,
            long amount,
            String currency,
            PaymentMethod paymentMethod,
            String idempotencyKey,
            PaymentStatus status,
            String providerPaymentId,
            int retryCount,
            Instant createdAt,
            Instant updatedAt) {

        this.id = id;
        this.userId = userId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.providerPaymentId = providerPaymentId;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setProviderPaymentId(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
        this.updatedAt = Instant.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = Instant.now();
    }
}