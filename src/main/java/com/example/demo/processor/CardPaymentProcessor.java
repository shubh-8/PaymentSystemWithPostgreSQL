package com.example.demo.processor;

import com.example.demo.model.Payment;
import com.example.demo.provider.ProviderResult;
import org.springframework.stereotype.Component;

@Component
public class CardPaymentProcessor implements PaymentProcessor {

    @Override
    public ProviderResult process(Payment payment) {
        // TODO: Implement card payment processing
        return null;
    }
}