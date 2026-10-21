package com.Sambhav.ecommerce.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.Sambhav.ecommerce.model.Category;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.exception.CategoryNotFoundException;
import com.Sambhav.ecommerce.repository.CategoryRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

    private CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public Category createCategory(Category category) {
        return categoryRepository.save(category);
    }
    public Category getCategoryById(int id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found with id: " + id));
    }
public List<Product> getProductsByCategory(int id) {

    Category category = getCategoryById(id);
    return category.getProducts();
}
public List<Category> getAllCategories() {
    return categoryRepository.findAll();
}
public Category updateCategory(int id, Category updatedCategory) {
    Category existingCategory = getCategoryById(id);

    existingCategory.setName(updatedCategory.getName());

    return categoryRepository.save(existingCategory);
}

public void deleteCategory(int id) {
    Category category = getCategoryById(id);
    if (productRepository.existsByCategory_Id(id)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot delete a category that still contains products. Move or delete its products first.");
    }
    categoryRepository.delete(category);
}
}