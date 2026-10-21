
package com.Sambhav.ecommerce.repository;

import com.Sambhav.ecommerce.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Integer> {

    Optional<Cart> findByUser_Id(Integer userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT DISTINCT c
        FROM Cart c
        LEFT JOIN FETCH c.items
        WHERE c.user.id = :userId
        """)
Optional<Cart> findByUser_IdForUpdate(
        @Param("userId") Integer userId);
}

