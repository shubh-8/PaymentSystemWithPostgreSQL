package com.example.demo.provider;

import com.example.demo.model.Payment;

public interface PaymentProvider {

    ProviderResult initiatePayment(Payment payment);
}