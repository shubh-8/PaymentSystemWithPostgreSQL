package com.example.demo.controller;

import com.example.demo.dto.PaymentWebhookRequest;
import com.example.demo.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    public ResponseEntity<String> processPaymentWebhook(
            @Valid @RequestBody PaymentWebhookRequest request) {

        paymentService.processWebhook(request);

        return ResponseEntity.ok("Webhook processed");
    }
}