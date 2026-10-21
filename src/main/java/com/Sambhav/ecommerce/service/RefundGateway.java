package com.Sambhav.ecommerce.service;

import java.math.BigDecimal;

public interface RefundGateway {
    RefundResult processRefund(String paymentReference, BigDecimal amount);
}
