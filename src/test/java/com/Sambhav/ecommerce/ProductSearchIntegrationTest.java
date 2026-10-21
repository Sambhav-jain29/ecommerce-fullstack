
package com.Sambhav.ecommerce;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.Sambhav.ecommerce.model.Category;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.repository.CategoryRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.service.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private String token;
    private String prefix;
    private Category mainCategory;
    private Category otherCategory;

    @BeforeEach
    void setUp() {

        token = jwtService.generateToken(
                "customer1@example.com",
                "CUSTOMER"
        );

        mainCategory = categoryRepository.findById(1)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Test category 1 not found"));

        otherCategory = categoryRepository.findAll()
                .stream()
                .filter(category -> !Objects.equals(
                        category.getId(),
                        mainCategory.getId()))
                .findFirst()
                .orElse(null);

        // Each test gets a unique product name.
        prefix = "SearchTest"
                + UUID.randomUUID().toString().replace("-", "");

        // Two products in the main category.
        createProduct(prefix + " Phone Alpha", 11001.0, mainCategory);
        createProduct(prefix + " Phone Beta", 11002.0, mainCategory);

        // Use a different category when one is available.
        // Otherwise, keep the product in the main category.
        Category laptopCategory = otherCategory != null
                ? otherCategory
                : mainCategory;

        createProduct(prefix + " Laptop", 11003.0, laptopCategory);
    }

    private void createProduct(
            String name,
            double price,
            Category category) {

        Product product = new Product();

        product.setName(name);
        product.setPrice(price);
        product.setStockQuantity(50);
        product.setCategory(category);

        productRepository.saveAndFlush(product);
    }

    @Test
    void searchShouldReturnOnlyMatchingProducts() throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix + " phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath(
                        "$.content[*].name",
                        containsInAnyOrder(
                                prefix + " Phone Alpha",
                                prefix + " Phone Beta"
                        )));
    }

    @Test
    void priceFilterShouldReturnProductsWithinRange()
            throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix)
                .param("minPrice", "11002")
                .param("maxPrice", "11003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath(
                        "$.content[*].name",
                        containsInAnyOrder(
                                prefix + " Phone Beta",
                                prefix + " Laptop"
                        )));
    }

    @Test
    void categoryFilterShouldReturnOnlyProductsInThatCategory()
            throws Exception {

        // This test needs at least two categories to prove
        // that products from another category are excluded.
        Assumptions.assumeTrue(
                otherCategory != null,
                "Skipping: database has only one category");

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix)
                .param("categoryId",
                        String.valueOf(mainCategory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath(
                        "$.content[*].name",
                        containsInAnyOrder(
                                prefix + " Phone Alpha",
                                prefix + " Phone Beta"
                        )));
    }

    @Test
    void sortingAndPaginationShouldWorkTogether()
            throws Exception {

        // First page: two most expensive products.
        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix)
                .param("page", "0")
                .param("size", "2")
                .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].name")
                        .value(prefix + " Laptop"))
                .andExpect(jsonPath("$.content[1].name")
                        .value(prefix + " Phone Beta"));

        // Second page: the remaining, cheapest product.
        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix)
                .param("page", "1")
                .param("size", "2")
                .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name")
                        .value(prefix + " Phone Alpha"));
    }

    @Test
    void searchWithNoMatchesShouldReturnEmptyPage()
            throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("name", prefix + " DoesNotExist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidPriceRangeShouldReturn400() throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("minPrice", "20000")
                .param("maxPrice", "10000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeOver100ShouldReturn400() throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("page", "0")
                .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedSortFieldShouldReturn400()
            throws Exception {

        mockMvc.perform(get("/products")
                .header("Authorization", "Bearer " + token)
                .param("sort", "stockQuantity,asc"))
                .andExpect(status().isBadRequest());
    }
}
