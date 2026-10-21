
package com.Sambhav.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;



import com.Sambhav.ecommerce.model.Product;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    // Name
    List<Product> findByNameContainingIgnoreCase(String name);

    // Category
    List<Product> findByCategory_Id(Integer categoryId);

    // Price
    List<Product> findByPriceBetween(double minPrice, double maxPrice);
    List<Product> findByPriceGreaterThanEqual(double minPrice);
    List<Product> findByPriceLessThanEqual(double maxPrice);

    // Category + price
    List<Product> findByCategory_IdAndPriceBetween(
            Integer categoryId, double minPrice, double maxPrice);

    List<Product> findByCategory_IdAndPriceGreaterThanEqual(
            Integer categoryId, double minPrice);

    List<Product> findByCategory_IdAndPriceLessThanEqual(
            Integer categoryId, double maxPrice);

    // Name + category
    List<Product> findByNameContainingIgnoreCaseAndCategory_Id(
            String name, Integer categoryId);

    // Name + price
    List<Product> findByNameContainingIgnoreCaseAndPriceBetween(
            String name, double minPrice, double maxPrice);

    List<Product> findByNameContainingIgnoreCaseAndPriceGreaterThanEqual(
            String name, double minPrice);

    List<Product> findByNameContainingIgnoreCaseAndPriceLessThanEqual(
            String name, double maxPrice);

    // Name + category + price
    List<Product> findByNameContainingIgnoreCaseAndCategory_IdAndPriceBetween(
            String name, Integer categoryId,
            double minPrice, double maxPrice);

    List<Product> findByNameContainingIgnoreCaseAndCategory_IdAndPriceGreaterThanEqual(
            String name, Integer categoryId, double minPrice);

    List<Product> findByNameContainingIgnoreCaseAndCategory_IdAndPriceLessThanEqual(
            String name, Integer categoryId, double maxPrice);

@Query("""
    SELECT p FROM Product p
    WHERE (:name IS NULL OR TRIM(:name) = ''
           OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))
      AND (:categoryId IS NULL OR p.category.id = :categoryId)
      AND (:minPrice IS NULL OR p.price >= :minPrice)
      AND (:maxPrice IS NULL OR p.price <= :maxPrice)
    """)
Page<Product> filterProducts(
        @Param("name") String name,
        @Param("categoryId") Integer categoryId,
        @Param("minPrice") Double minPrice,
        @Param("maxPrice") Double maxPrice,
        Pageable pageable);
        @Modifying
@Query("""
        UPDATE Product p
        SET p.stockQuantity = p.stockQuantity - :quantity
        WHERE p.id = :productId
          AND p.stockQuantity >= :quantity
        """)
int decrementStockIfAvailable(
        @Param("productId") Integer productId,
        @Param("quantity") Integer quantity);
        @Modifying
@Query("""
        UPDATE Product p
        SET p.stockQuantity = p.stockQuantity + :quantity
        WHERE p.id = :productId
        """)
int incrementStock(
        @Param("productId") Integer productId,
        @Param("quantity") Integer quantity);

    boolean existsByCategory_Id(Integer categoryId);
}
