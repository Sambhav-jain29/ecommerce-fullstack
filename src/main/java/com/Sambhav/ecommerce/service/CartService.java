
package com.Sambhav.ecommerce.service;

import com.Sambhav.ecommerce.dto.AddToCartRequest;
import com.Sambhav.ecommerce.dto.CartItemResponse;
import com.Sambhav.ecommerce.dto.CartResponse;
import com.Sambhav.ecommerce.dto.UpdateCartItemRequest;
import com.Sambhav.ecommerce.model.Cart;
import com.Sambhav.ecommerce.model.CartItem;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.model.User;
import com.Sambhav.ecommerce.repository.CartItemRepository;
import com.Sambhav.ecommerce.repository.CartRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CartResponse addItem(
            String email,
            AddToCartRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Product product = productRepository
                .findById(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"));

       Cart cart = cartRepository.findByUser_IdForUpdate(user.getId())
        .orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });

        CartItem item = cartItemRepository
                .findByCart_IdAndProduct_Id(
                        cart.getId(), product.getId())
                .orElse(null);

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(request.getQuantity());

            cart.getItems().add(item);
        } else {
            item.setQuantity(
                    item.getQuantity() + request.getQuantity());
        }

        cartItemRepository.save(item);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse getCart(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Cart cart = cartRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUser(user);
                    return cartRepository.save(newCart);
                });

        return buildCartResponse(cart);
    }

    private CartResponse buildCartResponse(Cart cart) {

        CartResponse response = new CartResponse();
        response.setCartId(cart.getId());

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {

            Product product = cartItem.getProduct();

            BigDecimal price = new BigDecimal(String.valueOf(product.getPrice()));

            BigDecimal subtotal = price.multiply(
                    BigDecimal.valueOf(cartItem.getQuantity()));

            CartItemResponse itemResponse = new CartItemResponse();
            itemResponse.setProductId(product.getId());
            itemResponse.setProductName(product.getName());
            itemResponse.setPrice(price);
            itemResponse.setQuantity(cartItem.getQuantity());
            itemResponse.setSubtotal(subtotal);

            items.add(itemResponse);
            total = total.add(subtotal);
        }

        response.setItems(items);
        response.setTotal(total);

        return response;
    }

    @Transactional
    public CartResponse updateItemQuantity(
            String email,
            Integer productId,
            UpdateCartItemRequest request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

        Cart cart = cartRepository.findByUser_IdForUpdate(user.getId())
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cart not found"));

        CartItem item = cartItemRepository
                .findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found in cart"));

        item.setQuantity(request.getQuantity());

        cartItemRepository.save(item);

        return buildCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(
            String email,
            Integer productId) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"));

       Cart cart = cartRepository.findByUser_IdForUpdate(user.getId())
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Cart not found"));

        CartItem item = cartItemRepository
                .findByCart_IdAndProduct_Id(
                        cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found in cart"));

        cart.getItems().removeIf(
                cartItem -> cartItem.getId().equals(item.getId()));

        return buildCartResponse(cart);
    }

}
