package com.Sambhav.ecommerce.service;

import com.Sambhav.ecommerce.dto.PaymentResponse;
import com.Sambhav.ecommerce.enums.OrderStatus;
import com.Sambhav.ecommerce.enums.PaymentStatus;
import com.Sambhav.ecommerce.enums.RefundStatus;
import com.Sambhav.ecommerce.model.Order;
import com.Sambhav.ecommerce.model.Payment;
import com.Sambhav.ecommerce.repository.OrderRepository;
import com.Sambhav.ecommerce.repository.PaymentRepository;
import com.Sambhav.ecommerce.repository.UserRepository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final RefundGateway refundGateway;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymentGateway paymentGateway,
            RefundGateway refundGateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentGateway = paymentGateway;
        this.refundGateway = refundGateway;
    }

    @Transactional
    public PaymentResponse payForOrder(
            String email,
            Integer orderId,
            String idempotencyKey) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters");
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Order order = orderRepository.findByIdAndUser_IdForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"));
        var existingPayment = paymentRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            if (!payment.getOrder().getId().equals(orderId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used for another order");
            }

            return toResponse(payment);
        }

        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Order is not awaiting payment");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setIdempotencyKey(idempotencyKey);

        Payment savedPayment = paymentRepository.save(payment);

        PaymentResult result = paymentGateway.processPayment(
                savedPayment.getPaymentReference(),
                savedPayment.getAmount());

        if (result.successful()) {
            savedPayment.setStatus(PaymentStatus.SUCCESS);
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else {
            savedPayment.setStatus(PaymentStatus.FAILED);
        }

        savedPayment.setProviderReference(result.providerReference());

        Payment finalPayment = paymentRepository.save(savedPayment);

        return toResponse(finalPayment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentHistory(String email, Integer orderId) {

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        orderRepository.findByIdAndUser_Id(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Order not found"));

        return paymentRepository
                .findByOrder_IdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();

        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getOrder().getId());
        response.setPaymentReference(payment.getPaymentReference());
        response.setProviderReference(payment.getProviderReference());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus().name());
        response.setCreatedAt(payment.getCreatedAt());
        response.setRefundStatus(payment.getRefundStatus().name());
        response.setRefundReference(payment.getRefundReference());
        response.setRefundedAt(payment.getRefundedAt());

        return response;
    }

    /** Refunds the successful payment for an order, if one exists. A successful refund is idempotent. */
    @Transactional
    public PaymentResponse refundSuccessfulPaymentIfPresent(String email, Integer orderId) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        orderRepository.findByIdAndUser_IdForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        Payment payment = paymentRepository
                .findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.SUCCESS)
                .orElse(null);
        if (payment == null) {
            return null;
        }

        if (payment.getRefundStatus() == RefundStatus.SUCCESS) {
            return toResponse(payment);
        }

        RefundResult result = refundGateway.processRefund(
                payment.getPaymentReference(), payment.getAmount());
        payment.setRefundReference(result.providerReference());
        if (result.successful()) {
            payment.setRefundStatus(RefundStatus.SUCCESS);
            payment.setRefundedAt(LocalDateTime.now());
        } else {
            payment.setRefundStatus(RefundStatus.FAILED);
            payment.setRefundedAt(null);
        }
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findSuccessfulRefundForOrder(String email, Integer orderId) {
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        orderRepository.findByIdAndUser_Id(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return paymentRepository
                .findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(orderId, PaymentStatus.SUCCESS)
                .map(this::toResponse)
                .orElse(null);
    }
}

/*# PaymentService — Brief Revision Notes

## 1. What does PaymentService do?

`PaymentService` handles payments for an order, updates the order status, and provides payment history.

## 2. Payment flow

1. Validate the `Idempotency-Key`.
2. Find the logged-in user.
3. Find and lock the user's order using `findByIdAndUser_IdForUpdate()`.
4. Check whether a payment already exists for that key.
5. Allow a new payment only if the order status is `PLACED`.
6. Save the payment and call the payment gateway.
7. If payment succeeds, set payment status to `SUCCESS` and order status to `CONFIRMED`.
8. If payment fails, set payment status to `FAILED`.
9. Save the result and return the payment response.

## 3. Important concepts

**Pessimistic locking**

The database locks the order while the transaction is working with it. Another request trying to lock that same order must wait. This helps prevent payment and cancellation requests from changing the order at the same time.

**Idempotency key**

An idempotency key identifies a payment request. If a request is repeated with the same key for the same order, the service returns the existing payment instead of starting another one.

Example: `Idempotency-Key: payment-123`

The database should also enforce uniqueness for this key. We still need to verify that rule in `Payment.java` and `PaymentRepository.java`.

**Authentication versus ownership**

Being logged in is not enough. The service also checks that the requested order belongs to the current user before allowing payment or showing payment history.

**Transaction (`@Transactional`)**

The payment operation runs inside a database transaction. The transaction helps keep database changes together, so they can be committed or rolled back as a unit.

## 4. Important status rules

- `PLACED` → payment can be attempted.
- Payment succeeds → order becomes `CONFIRMED`.
- Payment fails → payment becomes `FAILED`; the order stays `PLACED`.
- An order that is no longer `PLACED` cannot receive a new payment through this flow.
- A confirmed order can only be cancelled through the customer cancellation/refund workflow.

## 5. What still needs checking?

- Does the database enforce a unique idempotency key?
- Can an administrator mark an order `CONFIRMED` without successful payment?
- Do the payment and order status tests pass after any changes?

**Later, when integrating a real payment provider:** we must carefully handle cases where the provider charges the customer but the database update fails. We are postponing this extra work until after the main project and React integration.

### Remember

**Lock the order → check the payment key → verify order status → process payment → update statuses → return the result.** */