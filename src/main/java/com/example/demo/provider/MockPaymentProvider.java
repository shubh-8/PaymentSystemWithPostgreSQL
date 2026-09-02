package com.example.demo.provider;

import com.example.demo.model.Payment;
import com.example.demo.model.ProviderResultType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public ProviderResult initiatePayment(Payment payment) {

        String providerPaymentId =
                "provider_" + UUID.randomUUID();

        return new ProviderResult(
                ProviderResultType.ACCEPTED,
                providerPaymentId,
                "Payment accepted for processing"
        );
    }
}