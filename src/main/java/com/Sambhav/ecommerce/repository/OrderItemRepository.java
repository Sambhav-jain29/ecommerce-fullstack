package com.Sambhav.ecommerce.repository;

import com.Sambhav.ecommerce.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    boolean existsByProduct_Id(Integer productId);
}
