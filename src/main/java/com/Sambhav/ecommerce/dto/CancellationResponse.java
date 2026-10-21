package com.Sambhav.ecommerce.dto;

public class CancellationResponse {
    private OrderResponse order;
    private PaymentResponse payment;
    private boolean cancelled;
    private String message;

    public CancellationResponse() {
    }

    public CancellationResponse(OrderResponse order, PaymentResponse payment, boolean cancelled, String message) {
        this.order = order;
        this.payment = payment;
        this.cancelled = cancelled;
        this.message = message;
    }

    public OrderResponse getOrder() { return order; }
    public void setOrder(OrderResponse order) { this.order = order; }
    public PaymentResponse getPayment() { return payment; }
    public void setPayment(PaymentResponse payment) { this.payment = payment; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
