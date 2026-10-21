package com.Sambhav.ecommerce.repository;

import com.Sambhav.ecommerce.enums.PaymentStatus;
import com.Sambhav.ecommerce.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    List<Payment> findByOrder_IdOrderByCreatedAtDesc(Integer orderId);

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrder_IdAndStatus(Integer orderId, PaymentStatus status);

    java.util.Optional<Payment> findFirstByOrder_IdAndStatusOrderByCreatedAtDesc(
            Integer orderId, PaymentStatus status);
}