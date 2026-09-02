package com.example.demo.controller;

import com.example.demo.dto.CreatePaymentRequest;
import com.example.demo.dto.PaymentDetailsResponse;
import com.example.demo.dto.PaymentResponse;
import com.example.demo.model.Payment;
import com.example.demo.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        PaymentResponse response =
                paymentService.createPayment(
                        idempotencyKey,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailsResponse> getPayment(
            @PathVariable String paymentId) {

        Payment payment =
                paymentService.getPayment(paymentId);

        PaymentDetailsResponse response =
                new PaymentDetailsResponse(
                        payment.getId(),
                        payment.getUserId(),
                        payment.getMerchantId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getPaymentMethod(),
                        payment.getStatus(),
                        payment.getProviderPaymentId(),
                        payment.getRetryCount()
                );

        return ResponseEntity.ok(response);
    }
}