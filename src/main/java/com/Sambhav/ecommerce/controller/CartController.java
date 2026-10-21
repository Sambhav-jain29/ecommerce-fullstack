
package com.Sambhav.ecommerce.controller;

import com.Sambhav.ecommerce.dto.AddToCartRequest;
import com.Sambhav.ecommerce.dto.CartResponse;
import com.Sambhav.ecommerce.dto.UpdateCartItemRequest;
import com.Sambhav.ecommerce.service.CartService;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/items")
    public CartResponse addItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request) {

        String email = userDetails.getUsername();

        return cartService.addItem(email, request);
    }

    @GetMapping
    public CartResponse getCart(
            @AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();

        return cartService.getCart(email);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItemQuantity(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        String email = userDetails.getUsername();

        return cartService.updateItemQuantity(
                email, productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer productId) {

        String email = userDetails.getUsername();

        return cartService.removeItem(email, productId);
    }
}
