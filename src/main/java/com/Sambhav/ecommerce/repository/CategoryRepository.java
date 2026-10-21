package com.Sambhav.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.Sambhav.ecommerce.model.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
}