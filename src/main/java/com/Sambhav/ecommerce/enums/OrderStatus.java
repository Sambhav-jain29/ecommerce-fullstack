package com.Sambhav.ecommerce.enums;

public enum OrderStatus {
    PLACED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
/*
 * Java provides an enum for situations where a value should come from a fixed
 * set of allowed choices.
 * For our order status, the allowed values will be:
 * PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
 * This makes the possible statuses explicit and helps prevent spelling
 * mistakes.
 */