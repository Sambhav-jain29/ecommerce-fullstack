package com.Sambhav.ecommerce.controller;

import com.Sambhav.ecommerce.dto.OrderResponse;
import com.Sambhav.ecommerce.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public Page<OrderResponse> getAllOrders(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (pageable.getPageSize() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page size cannot exceed 100");
        }
        for (Sort.Order order : pageable.getSort()) {
            String field = order.getProperty();
            if (!field.equals("id") && !field.equals("createdAt")
                    && !field.equals("status") && !field.equals("totalAmount")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field: " + field);
            }
        }
        return orderService.getAllOrders(pageable);
    }
}
