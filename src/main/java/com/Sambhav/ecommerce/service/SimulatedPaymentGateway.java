
package com.Sambhav.ecommerce.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Value("${payment.simulator.success:true}")
    private boolean paymentSuccessful;

    @Override
    public PaymentResult processPayment(
            String paymentReference,
            BigDecimal amount) {

        String providerReference = "SIM-" + UUID.randomUUID();

        return new PaymentResult(
                paymentSuccessful,
                providerReference
        );
    }
}
