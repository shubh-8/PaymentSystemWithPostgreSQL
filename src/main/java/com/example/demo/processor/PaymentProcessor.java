package com.example.demo.processor;

import com.example.demo.model.Payment;
import com.example.demo.provider.ProviderResult;

public interface PaymentProcessor {

    ProviderResult process(Payment payment);
}