package com.Sambhav.ecommerce.service;

public record PaymentResult(
        boolean successful,
        String providerReference) {
}