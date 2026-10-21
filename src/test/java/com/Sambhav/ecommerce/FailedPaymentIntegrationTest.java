
package com.Sambhav.ecommerce;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "payment.simulator.success=false")
class FailedPaymentIntegrationTest {

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
    void failedPaymentShouldLeaveOrderPlaced() throws Exception {
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

        MvcResult checkoutResult = mockMvc.perform(
                post("/orders/checkout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        Integer orderId = JsonPath.read(
                checkoutResult.getResponse().getContentAsString(),
                "$.orderId"
        );

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "failed-payment-" + orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(get("/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"));
    }
}
