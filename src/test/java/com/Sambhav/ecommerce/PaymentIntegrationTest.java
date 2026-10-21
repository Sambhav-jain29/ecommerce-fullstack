
package com.Sambhav.ecommerce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.service.JwtService;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void prepareTestProductStock() {
        Product product = productRepository.findById(2)
                .orElseThrow(() ->
                        new IllegalStateException("Test product 2 not found"));

        product.setStockQuantity(100);
        productRepository.save(product);
    }

    @Test
    void successfulPaymentShouldConfirmOrder() throws Exception {
        String token = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId");

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "payment-success-" + orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentReference").isNotEmpty())
                .andExpect(jsonPath("$.providerReference").isNotEmpty());

        mockMvc.perform(get("/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void userCannotPayForAnotherUsersOrder() throws Exception {
        String adminToken = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

        String customerToken = jwtService.generateToken(
                "customer1@example.com", "CUSTOMER");

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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId");

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken)
                .header("Idempotency-Key", "unauthorized-payment-" + orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    void payingForAlreadyConfirmedOrderShouldReturn409() throws Exception {
        String token = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId");

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "first-payment-" + orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "second-payment-" + orderId))
                .andExpect(status().isConflict());
    }

    @Test
    void userCannotViewAnotherUsersPaymentHistory() throws Exception {
        String adminToken = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

        String customerToken = jwtService.generateToken(
                "customer1@example.com", "CUSTOMER");

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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId");

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", "history-payment-" + orderId))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void paymentHistoryShouldBeEmptyBeforeAnyPayment() throws Exception {
        String token = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId");

        mockMvc.perform(get("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void paymentHistoryForMissingOrderShouldReturn404() throws Exception {
        String token = jwtService.generateToken(
                "storeadmin@example.com", "ADMIN");

        mockMvc.perform(get("/payments/orders/999999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
    @Test
    void customerCancellationAfterSuccessfulPaymentRefundsOnceAndRestoresStock() throws Exception {
        String token = jwtService.generateToken("storeadmin@example.com", "ADMIN");
        Product product = productRepository.findById(2).orElseThrow();
        int initialStock = product.getStockQuantity();

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
        MvcResult checkout = mockMvc.perform(post("/orders/checkout")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        Integer orderId = JsonPath.read(checkout.getResponse().getContentAsString(), "$.orderId");
        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "refund-success-" + orderId))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUCCESS"));

        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment.refundStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.payment.refundReference").isNotEmpty());

        org.junit.jupiter.api.Assertions.assertEquals(initialStock, productRepository.findById(2).orElseThrow().getStockQuantity());
        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelled").value(true));
        org.junit.jupiter.api.Assertions.assertEquals(initialStock, productRepository.findById(2).orElseThrow().getStockQuantity());

        mockMvc.perform(get("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refundStatus").value("SUCCESS"));
    }


    @Test
    void customerCancellationOfUnpaidOrderRestoresStockOnlyOnce() throws Exception {
        String token = jwtService.generateToken("customer1@example.com", "CUSTOMER");
        int initialStock = productRepository.findById(2).orElseThrow().getStockQuantity();

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

        MvcResult checkout = mockMvc.perform(post("/orders/checkout")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        Integer orderId = JsonPath.read(checkout.getResponse().getContentAsString(), "$.orderId");
        assertEquals(initialStock - 2, productRepository.findById(2).orElseThrow().getStockQuantity());

        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true))
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payment").doesNotExist());
        assertEquals(initialStock, productRepository.findById(2).orElseThrow().getStockQuantity());

        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled").value(true));
        assertEquals(initialStock, productRepository.findById(2).orElseThrow().getStockQuantity());
    }

    @Test
    void customerCannotCancelAnotherUsersOrder() throws Exception {
        String ownerToken = jwtService.generateToken("storeadmin@example.com", "ADMIN");
        String otherToken = jwtService.generateToken("customer1@example.com", "CUSTOMER");
        mockMvc.perform(post("/cart/items")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "productId": 2,
                            "quantity": 1
                        }
                        """))
                .andExpect(status().isOk());
        MvcResult checkout = mockMvc.perform(post("/orders/checkout")
                .header("Authorization", "Bearer " + ownerToken)).andExpect(status().isCreated()).andReturn();
        Integer orderId = JsonPath.read(checkout.getResponse().getContentAsString(), "$.orderId");
        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + otherToken)).andExpect(status().isNotFound());
    }

}
