

package com.Sambhav.ecommerce.controller;

import com.Sambhav.ecommerce.dto.OrderResponse;
import com.Sambhav.ecommerce.dto.CancellationResponse;
import com.Sambhav.ecommerce.dto.UpdateOrderStatusRequest;
import com.Sambhav.ecommerce.service.OrderService;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();

        return orderService.checkout(email);
    }

@GetMapping
public List<OrderResponse> getMyOrders(
        @AuthenticationPrincipal UserDetails userDetails) {

    String email = userDetails.getUsername();

    return orderService.getMyOrders(email);
}
@PostMapping("/{orderId}/cancel")
public CancellationResponse cancelOrder(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable Integer orderId) {
    return orderService.cancelMyOrder(userDetails.getUsername(), orderId);
}

@GetMapping("/{orderId}")
public OrderResponse getOrderById(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable Integer orderId) {

    String email = userDetails.getUsername();

    return orderService.getOrderById(email, orderId);
}
@PatchMapping("/{orderId}/status")
public OrderResponse updateOrderStatus(
        @PathVariable Integer orderId,
        @Valid @RequestBody UpdateOrderStatusRequest request) {

    return orderService.updateOrderStatus(orderId, request);
}

}
