
package com.Sambhav.ecommerce.repository;

import com.Sambhav.ecommerce.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository
        extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByCart_Id(Integer cartId);

    Optional<CartItem> findByCart_IdAndProduct_Id(
            Integer cartId,
            Integer productId
    );
    boolean existsByProduct_Id(Integer productId);
}

