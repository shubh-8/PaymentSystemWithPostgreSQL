package com.example.demo.dto;

import com.example.demo.model.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    @NotBlank
    private String userId;
    
    @NotBlank
    private String merchantId;
    
    @Positive
    private long amount;
    
    @NotBlank
    private String currency;
    
    @NotNull
    private PaymentMethod paymentMethod;
}