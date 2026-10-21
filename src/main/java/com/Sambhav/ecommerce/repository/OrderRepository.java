
package com.Sambhav.ecommerce.repository;

import com.Sambhav.ecommerce.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUser_IdOrderByCreatedAtDesc(Integer userId);

    Optional<Order> findByIdAndUser_Id(Integer orderId, Integer userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUser_IdForUpdate(
            @Param("orderId") Integer orderId,
            @Param("userId") Integer userId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Order o WHERE o.id = :orderId")
Optional<Order> findByIdForUpdate(
        @Param("orderId") Integer orderId
);
}
