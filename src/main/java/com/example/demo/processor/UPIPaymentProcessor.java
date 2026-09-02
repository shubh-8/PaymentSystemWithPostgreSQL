package com.example.demo.processor;

import com.example.demo.model.Payment;
import com.example.demo.provider.ProviderResult;
import org.springframework.stereotype.Component;

@Component
public class UPIPaymentProcessor implements PaymentProcessor {

    @Override
    public ProviderResult process(Payment payment) {
        // TODO: Implement UPI payment processing
        return null;
    }
}