package com.example.demo.processor;

import com.example.demo.model.Payment;
import com.example.demo.model.PaymentMethod;
import com.example.demo.provider.ProviderResult;

public interface PaymentProcessor {

    PaymentMethod supportedMethod();

    ProviderResult process(Payment payment);
}