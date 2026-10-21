package com.Sambhav.ecommerce.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Simulates a provider refund; no real money is moved. */
@Component
public class SimulatedRefundGateway implements RefundGateway {

    @Value("${payment.simulator.refund-success:true}")
    private boolean refundSuccessful;

    @Override
    public RefundResult processRefund(String paymentReference, BigDecimal amount) {
        String providerReference = "SIM-REFUND-" + UUID.randomUUID();
        return new RefundResult(refundSuccessful, providerReference);
    }
}
