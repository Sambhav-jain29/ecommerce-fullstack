package com.Sambhav.ecommerce.service;

import java.math.BigDecimal;

public interface PaymentGateway {

    PaymentResult processPayment(
            String paymentReference,
            BigDecimal amount);
}