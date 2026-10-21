
package com.Sambhav.ecommerce;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.Sambhav.ecommerce.model.Payment;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.repository.PaymentRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.service.JwtService;
import com.Sambhav.ecommerce.service.PaymentGateway;
import com.Sambhav.ecommerce.service.PaymentResult;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentRetryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void prepareTestProductStock() {
        Product product = productRepository.findById(2)
                .orElseThrow(() ->
                        new IllegalStateException("Test product 2 not found"));

        product.setStockQuantity(100);
        productRepository.save(product);
    }

    @Test
    void failedPaymentCanBeRetriedSuccessfully() throws Exception {
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

        // First attempt fails.
        when(paymentGateway.processPayment(
                anyString(), any(BigDecimal.class)))
                .thenReturn(new PaymentResult(false, "SIM-FAIL-001"));

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "attempt-1-" + orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // A new key represents a new attempt, which succeeds.
        when(paymentGateway.processPayment(
                anyString(), any(BigDecimal.class)))
                .thenReturn(new PaymentResult(true, "SIM-SUCCESS-002"));

        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "attempt-2-" + orderId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        List<Payment> payments =
                paymentRepository.findByOrder_IdOrderByCreatedAtDesc(orderId);

        assertEquals(2, payments.size());
        assertEquals("SUCCESS", payments.get(0).getStatus().name());
        assertEquals("FAILED", payments.get(1).getStatus().name());

        mockMvc.perform(get("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    void repeatingSameIdempotencyKeyDoesNotCreateAnotherPayment()
            throws Exception {
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

        when(paymentGateway.processPayment(
                anyString(), any(BigDecimal.class)))
                .thenReturn(new PaymentResult(true, "SIM-IDEMPOTENT-001"));

        String key = "same-request-" + orderId;

        MvcResult firstResult = mockMvc.perform(
                post("/payments/orders/" + orderId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();

        String firstPaymentReference = JsonPath.read(
                firstResult.getResponse().getContentAsString(),
                "$.paymentReference");

        // Same key: return the original payment.
        mockMvc.perform(post("/payments/orders/" + orderId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentReference")
                        .value(firstPaymentReference))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        List<Payment> payments =
                paymentRepository.findByOrder_IdOrderByCreatedAtDesc(orderId);

        assertEquals(1, payments.size());

        // The gateway should only have been called for the first request.
        verify(paymentGateway, times(1))
                .processPayment(anyString(), any(BigDecimal.class));
    }
}
