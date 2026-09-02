package com.example.demo.dto;

import com.example.demo.model.PaymentMethod;
import com.example.demo.model.PaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentDetailsResponse {

    private String paymentId;
    private String userId;
    private String merchantId;
    private long amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String providerPaymentId;
    private int retryCount;
}