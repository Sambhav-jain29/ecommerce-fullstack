package com.Sambhav.ecommerce.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import com.Sambhav.ecommerce.model.Category;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.service.CategoryService;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<Category> createCategory(
            @Valid @RequestBody Category category) {

        Category createdCategory = categoryService.createCategory(category);

        return ResponseEntity.status(201).body(createdCategory);
    }

    @GetMapping
    public List<Category> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategory(@PathVariable int id) {

        Category category = categoryService.getCategoryById(id);

        return ResponseEntity.ok(category);
    }

    @GetMapping("/{id}/products")
    public List<Product> getProductsByCategory(@PathVariable int id) {

        return categoryService.getProductsByCategory(id);
    }
    @PutMapping("/{id}")//put gerenties idempotency and patch does not , meaning that if you make the same request multiple times, 
    // the result will be the same. This is useful for updating resources because it ensures that the resource will be in a consistent state after the update.
public ResponseEntity<Category> updateCategory(
        @PathVariable int id,
        @Valid @RequestBody Category category) {

    Category updatedCategory =
            categoryService.updateCategory(id, category);

    return ResponseEntity.ok(updatedCategory);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteCategory(
        @PathVariable int id) {

    categoryService.deleteCategory(id);

    return ResponseEntity.noContent().build();
}
}