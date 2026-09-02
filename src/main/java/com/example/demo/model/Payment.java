package com.example.demo.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    private String id;
    private String userId;
    private String merchantId;

    private long amount;
    private String currency;

    private PaymentMethod paymentMethod;

    private String idempotencyKey;
    private PaymentStatus status;
    private String providerPaymentId;

    private int retryCount;

    private Instant createdAt;
    private Instant updatedAt;
}
