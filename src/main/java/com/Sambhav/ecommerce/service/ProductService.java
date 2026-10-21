package com.Sambhav.ecommerce.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.Sambhav.ecommerce.dto.CreateProductRequest;
import com.Sambhav.ecommerce.dto.UpdateProductRequest;
import com.Sambhav.ecommerce.exception.CategoryNotFoundException;
import com.Sambhav.ecommerce.exception.ProductNotFoundException;
import com.Sambhav.ecommerce.model.Category;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.repository.CategoryRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.repository.CartItemRepository;
import com.Sambhav.ecommerce.repository.OrderItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          CartItemRepository cartItemRepository, OrderItemRepository orderItemRepository) { // controller
                                                                                                        // needs service
                                                                                                        // and service
                                                                                                        // needs
                                                                                                        // repository so
                                                                                                        // we are
                                                                                                        // injecting
                                                                                                        // repository in
                                                                                                        // service class
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderItemRepository = orderItemRepository;
    }

    // public List<Product> getProducts() { // URL= GET /products
    // return productRepository.findAll(); // findAll() is a method provided by
    // JpaRepository that returns all the
    // // products in the database.
    // // Hibernate will automatically generate the SQL query to fetch all the
    // products
    // // from the database and map them to Product objects.
    // }

    // PAGINATION-->
    // Imagine your e-commerce store has 10,000 products. Returning every product in
    // one response would be inefficient. Pagination lets the client request a small
    // group at a time.

    // For example:
    // - Page 0 → products 1–10
    // - Page 1 → products 11–20
    // - Page 2 → products 21–30
    // findAll() returns every product from the database. If your store eventually
    // has 10,000 products, the API may send all 10,000 in one response.
    // Instead, we want the client to choose how many products to receive at a time.
    // For example, the client could request:
    // GET /products?page=0&size=10

    // This means:
    // - page=0 — return the first page. Pages start at zero.
    // - size=10 — return at most 10 products.

    public Page<Product> getProducts(Pageable pageable) {// Now, findAll(pageable) returns a Page<Product> containing
                                                         // only the requested page and information about the overall
                                                         // results.
        // The pageable parameter tells the repository which page and how many products
        // to fetch.
        return productRepository.findAll(pageable);
    }

    // public Product createProduct(Product product) {

    // return productRepository.save(product);

    // }

    public Product createProduct(CreateProductRequest request) {

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with id: " + request.getCategoryId()));

        Product product = new Product();

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());

        product.setCategory(category);
        product.setImageUrl(request.getImageUrl() == null || request.getImageUrl().isBlank()
                ? null : request.getImageUrl().trim());

        return productRepository.save(product);
    }

    public Product getProductById(int id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }

    // public Product updateProduct(int id, UpdateProductRequest request) {
    // Product product = productRepository.findById(id)
    // .orElseThrow(() -> new ProductNotFoundException(
    // "Product not found with id: " + id));

    // product.setPrice(request.getPrice());

    // return productRepository.save(product);
    // }

    
public Product updateProduct(int id, UpdateProductRequest request) {

    Product product = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(
                    "Product not found with id: " + id));

    if (request.getName() != null) {
        if (request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product name cannot be blank");
        }
        product.setName(request.getName().trim());
    }

    if (request.getPrice() != null) {
        product.setPrice(request.getPrice());
    }

    if (request.getCategoryId() != null) {
        Category category = categoryRepository
                .findById(request.getCategoryId())
                .orElseThrow(() -> new CategoryNotFoundException(
                        "Category not found with id: "
                                + request.getCategoryId()));

        product.setCategory(category);
    }

    if (request.getStockQuantity() != null) {
        product.setStockQuantity(request.getStockQuantity());
    }

    if (request.getImageUrl() != null) {
        product.setImageUrl(request.getImageUrl().isBlank() ? null : request.getImageUrl().trim());
    }

    return productRepository.save(product);
}

    public void deleteProduct(int id) {

        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(
                    "Product not found with id: " + id);
        }

        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This product appears in order history and cannot be deleted. Set its stock to 0 instead.");
        }
        if (cartItemRepository.existsByProduct_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This product is in a customer's cart. Remove it from carts before deleting it.");
        }
        productRepository.deleteById(id);
    }

    public List<Product> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Product> getProductsByCategory(Integer categoryId) {
        return productRepository.findByCategory_Id(categoryId);
    }

    public List<Product> getProductsByPrice(double minPrice, double maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice);
    }

    public List<Product> getProductsByCategoryAndPrice(
            Integer categoryId,
            double minPrice,
            double maxPrice) {

        return productRepository.findByCategory_IdAndPriceBetween(
                categoryId, minPrice, maxPrice);
    }

   
public Page<Product> filterProducts(
        String name,
        Integer categoryId,
        Double minPrice,
        Double maxPrice,
        Pageable pageable) {

    return productRepository.filterProducts(
            name, categoryId, minPrice, maxPrice, pageable);
}

}