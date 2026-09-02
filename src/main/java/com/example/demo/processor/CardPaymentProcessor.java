package com.example.demo.processor;

import com.example.demo.model.Payment;
import com.example.demo.model.PaymentMethod;
import com.example.demo.provider.PaymentProvider;
import com.example.demo.provider.ProviderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CardPaymentProcessor implements PaymentProcessor {

    private final PaymentProvider paymentProvider;

    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.CARD;
    }

    @Override
    public ProviderResult process(Payment payment) {
        return paymentProvider.initiatePayment(payment);
    }
}