
package com.Sambhav.ecommerce;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.service.JwtService;
import com.jayway.jsonpath.JsonPath;

import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.contains;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
private JwtService jwtService;
@Autowired
private ProductRepository productRepository;

    
@Test
void productsWithoutTokenShouldReturn200() throws Exception {
    mockMvc.perform(get("/products"))
            .andExpect(status().isOk());
}

    @Test
void customerCannotCreateProduct() throws Exception {

    String token = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    mockMvc.perform(post("/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isForbidden());
}
@Test
void adminCanCreateProduct() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String productName =
            "Integration Test Product " + System.nanoTime();

    String requestBody = """
            {
                "name": "%s",
                "price": 999,
                "categoryId": 1,
                "stockQuantity": 10,
                "imageUrl": "https://example.com/test-product.jpg"
            }
            """.formatted(productName);

    mockMvc.perform(post("/products")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(productName))
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/test-product.jpg"));
}
@Test
void adminCanUpdateProductNameAndImage() throws Exception {
    String token = jwtService.generateToken("storeadmin@example.com", "ADMIN");
    String updatedName = "Updated Product " + System.nanoTime();

    mockMvc.perform(patch("/products/2")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "name": "%s",
                        "imageUrl": "https://example.com/updated-product.jpg"
                    }
                    """.formatted(updatedName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(updatedName))
            .andExpect(jsonPath("$.imageUrl").value("https://example.com/updated-product.jpg"));
}

@Test
void customerCanViewProducts() throws Exception {

    String token = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    mockMvc.perform(get("/products")
            .header("Authorization", "Bearer " + token)).andDo(print())
            .andExpect(status().isOk());
}
@Test
void authenticatedUserCanViewCart() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(get("/cart")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isOk());
}

@Test
void authenticatedUserCanAddProductToCart() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String requestBody = """
            {
                "productId": 2,
                "quantity": 2
            }
            """;

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk());
}
@Test
void addingSameProductTwiceShouldIncreaseQuantity()
        throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String requestBody1 = """
            {
                "productId": 2,
                "quantity": 1
            }
            """;

    String requestBody2 = """
            {
                "productId": 2,
                "quantity": 2
            }
            """;

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody1))
            .andExpect(status().isOk());

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody2))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                    "$.items[0].quantity"
            ).value(3));
}
@Test
void updatingCartItemShouldReplaceQuantity() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    mockMvc.perform(put("/cart/items/2")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "quantity": 4
                    }
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                    "$.items[?(@.productId == 2)].quantity",
                    contains(4)
            ));
}
@Test
void removingCartItemShouldRemoveItFromCart() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    mockMvc.perform(delete("/cart/items/2")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath(
                    "$.items[?(@.productId == 2)]",
                    hasSize(0)
            ));
}
@Test
void addingProductWithZeroQuantityShouldReturn400()
        throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 0
                    }
                    """))
            .andDo(print())
            .andExpect(status().isBadRequest());
}
@Test
void customerCannotSeeAnotherUsersCartItems() throws Exception {

    String adminToken = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String customerToken = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    // Add a product to the admin's cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // The customer should see their own empty cart.
    mockMvc.perform(get("/cart")
            .header("Authorization", "Bearer " + customerToken))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
}
@Test
void addingNonexistentProductShouldReturn404()
        throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 999999,
                        "quantity": 1
                    }
                    """))
            .andDo(print())
            .andExpect(status().isNotFound());
}
@Test
void checkoutShouldCreateOrderAndClearCart() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // Add two units of product 2 to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 2
                    }
                    """))
            .andExpect(status().isOk());

    // Complete checkout.
    mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").isNumber())
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").exists())
            .andExpect(jsonPath("$.totalAmount").exists());

    // Confirm that checkout emptied the cart.
    mockMvc.perform(get("/cart")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
}
@Test
void checkoutWithEmptyCartShouldReturn400() throws Exception {

    String token = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isBadRequest());
}
@Test
void authenticatedUserCanViewOrderHistory() throws Exception {

    String token = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    mockMvc.perform(get("/orders")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
}
@Test
void userCanViewTheirOwnOrder() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // Add a product to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // Create an order from the cart.
    MvcResult result = mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

    // Extract the newly generated order ID from the response.
    String responseBody = result.getResponse().getContentAsString();
    Integer orderId = JsonPath.read(responseBody, "$.orderId");

    // Retrieve that specific order.
    mockMvc.perform(get("/orders/" + orderId)
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(orderId))
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.items", hasSize(1)));
}

@Test
void userCannotViewAnotherUsersOrder() throws Exception {

    String adminToken = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String customerToken = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    // Admin adds a product to their cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // Admin checks out and creates an order.
    MvcResult result = mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    Integer orderId = JsonPath.read(responseBody, "$.orderId");

    // Customer attempts to view the admin's order.
    mockMvc.perform(get("/orders/" + orderId)
            .header("Authorization", "Bearer " + customerToken))
            .andDo(print())
            .andExpect(status().isNotFound());
}
@Test
void adminCanUpdateOrderStatus() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // Step 1: Add a product to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // Step 2: Create an order.
    MvcResult result = mockMvc.perform(
            post("/orders/checkout")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

    Integer orderId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.orderId"
    );

    // Step 3: Pay for the order successfully.
    mockMvc.perform(post("/payments/orders/" + orderId)
            .header("Authorization", "Bearer " + token)
            .header("Idempotency-Key", "status-test-" + orderId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

    // Step 4: The administrator can now mark it as SHIPPED.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "SHIPPED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SHIPPED"));
}
@Test
void customerCannotUpdateOrderStatus() throws Exception {

    String adminToken = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    String customerToken = jwtService.generateToken(
            "customer1@example.com",
            "CUSTOMER"
    );

    // Admin creates an order.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    MvcResult result = mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn();

    Integer orderId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.orderId"
    );

    // Customer attempts to change the order status.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + customerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "CONFIRMED"
                    }
                    """))
            .andDo(print())
            .andExpect(status().isForbidden());
}

@Test
void adminCannotSkipOrderStatusSteps() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // Add a product to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // Create an order. Its initial status should be PLACED.
    MvcResult result = mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

    Integer orderId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.orderId"
    );

    // Try to skip directly from PLACED to DELIVERED.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "DELIVERED"
                    }
                    """))
            .andDo(print())
            .andExpect(status().isBadRequest());
}
@BeforeEach
void prepareTestProductStock() {
    Product product = productRepository.findById(2)
            .orElseThrow(() ->
                    new IllegalStateException("Test product 2 not found"));

    product.setStockQuantity(100);
    productRepository.save(product);
}
@Test
void checkoutShouldReduceProductStock() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    Product product = productRepository.findById(2)
            .orElseThrow();

    int initialStock = product.getStockQuantity();

    // Add two units to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 2
                    }
                    """))
            .andExpect(status().isOk());

    // Complete checkout.
    mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated());

    // Verify that two units were deducted.
    Product updatedProduct = productRepository.findById(2)
            .orElseThrow();

    org.junit.jupiter.api.Assertions.assertEquals(
            initialStock - 2,
            updatedProduct.getStockQuantity()
    );
}
@Test
void checkoutWithInsufficientStockShouldReturn409() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // The test setup assigns product 2 a stock of 100.
    // Add 101 units to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 101
                    }
                    """))
            .andExpect(status().isOk());

    // Checkout must be rejected because stock is only 100.
    mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andDo(print())
            .andExpect(status().isConflict());
            // Stock should remain unchanged.
Product productAfter = productRepository.findById(2)
        .orElseThrow();

org.junit.jupiter.api.Assertions.assertEquals(
        100,
        productAfter.getStockQuantity()
);

// The cart should remain available for the customer to adjust.
mockMvc.perform(get("/cart")
        .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath(
                "$.items[?(@.productId == 2)].quantity",
                contains(101)
        ));
}
@Test
void adminCanUpdateProductStock() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

   mockMvc.perform(patch("/products/2")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "price": 999,
                        "categoryId": 1,
                        "stockQuantity": 25
                    }
                    """))
            .andDo(print())
            .andExpect(status().isOk());

    Product updatedProduct = productRepository.findById(2)
            .orElseThrow();

    org.junit.jupiter.api.Assertions.assertEquals(
            25,
            updatedProduct.getStockQuantity()
    );
}
@Test
void updatingProductWithoutStockShouldPreserveExistingStock()
        throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    Product product = productRepository.findById(2)
            .orElseThrow();

    int initialStock = product.getStockQuantity();

    mockMvc.perform(patch("/products/2")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "price": 999,
                        "categoryId": 1
                    }
                    """))
            .andDo(print())
            .andExpect(status().isOk());

    Product updatedProduct = productRepository.findById(2)
            .orElseThrow();

    org.junit.jupiter.api.Assertions.assertEquals(
            initialStock,
            updatedProduct.getStockQuantity()
    );
}
@Test
void adminCannotSetNegativeProductStock() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    mockMvc.perform(patch("/products/2")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "stockQuantity": -5
                    }
                    """))
            .andDo(print())
            .andExpect(status().isBadRequest());
}

@Test
void cancellingOrderRestoresStockOnlyOnce() throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    Product product = productRepository.findById(2)
            .orElseThrow();

    int initialStock = product.getStockQuantity();

    // Add two units to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 2
                    }
                    """))
            .andExpect(status().isOk());

    // Create the order.
    MvcResult result = mockMvc.perform(post("/orders/checkout")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

    Integer orderId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.orderId"
    );

    // Cancel the order.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "CANCELLED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

    Product restoredProduct = productRepository.findById(2)
            .orElseThrow();

    org.junit.jupiter.api.Assertions.assertEquals(
            initialStock,
            restoredProduct.getStockQuantity()
    );

    // A second cancellation must be rejected.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "CANCELLED"
                    }
                    """))
            .andExpect(status().isBadRequest());

    Product finalProduct = productRepository.findById(2)
            .orElseThrow();

    org.junit.jupiter.api.Assertions.assertEquals(
            initialStock,
            finalProduct.getStockQuantity()
    );
}

@Test
void adminCannotConfirmOrderWithoutSuccessfulPayment()
        throws Exception {

    String token = jwtService.generateToken(
            "storeadmin@example.com",
            "ADMIN"
    );

    // 1. Add a product to the cart.
    mockMvc.perform(post("/cart/items")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "productId": 2,
                        "quantity": 1
                    }
                    """))
            .andExpect(status().isOk());

    // 2. Create an order. It should be PLACED.
    MvcResult result = mockMvc.perform(
            post("/orders/checkout")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();

    Integer orderId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.orderId"
    );

    // 3. Try to confirm the order without making payment.
    mockMvc.perform(patch("/orders/" + orderId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                    {
                        "status": "CONFIRMED"
                    }
                    """))
            .andExpect(status().isBadRequest());

    // 4. The order must remain PLACED.
    mockMvc.perform(get("/orders/" + orderId)
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PLACED"));
}


@Test
void onlyAdminsCanViewAllOrders() throws Exception {
    String adminToken = jwtService.generateToken("storeadmin@example.com", "ADMIN");
    String customerToken = jwtService.generateToken("customer1@example.com", "CUSTOMER");

    mockMvc.perform(get("/admin/orders?page=0&size=10")
            .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.totalPages").exists());

    mockMvc.perform(get("/admin/orders?page=0&size=10")
            .header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isForbidden());
}


}

/*Test scenario	Expected HTTP status	What it proves
Request without a token	                401	Authentication is required.
Customer creates a product	            403	Customers cannot perform admin-only operations.
Admin creates a product	                201	Admin authorization works.
Customer views products	                200	Customers can browse products. */