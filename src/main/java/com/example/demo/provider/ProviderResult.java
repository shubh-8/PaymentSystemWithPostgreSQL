package com.example.demo.provider;

import com.example.demo.model.ProviderResultType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResult {
    private ProviderResultType resultType;
    private String providerPaymentId;
    private String errorMessage;
}
