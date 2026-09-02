package com.example.demo.dto;

import com.example.demo.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentResponse {
    private String paymentId;
    private PaymentStatus status;
}