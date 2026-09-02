package com.example.demo.dto;

import com.example.demo.model.PaymentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookRequest {
    @NotBlank
    private String eventId;
    @NotBlank
    private String providerPaymentId;
    @NotNull
    private PaymentStatus status;
}