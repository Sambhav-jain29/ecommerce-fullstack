package com.Sambhav.ecommerce.service;

import com.Sambhav.ecommerce.dto.CancellationResponse;
import com.Sambhav.ecommerce.dto.OrderItemResponse;
import com.Sambhav.ecommerce.dto.OrderResponse;
import com.Sambhav.ecommerce.dto.PaymentResponse;
import com.Sambhav.ecommerce.dto.UpdateOrderStatusRequest;
import com.Sambhav.ecommerce.enums.OrderStatus;
import com.Sambhav.ecommerce.enums.PaymentStatus;
import com.Sambhav.ecommerce.model.Cart;
import com.Sambhav.ecommerce.model.CartItem;
import com.Sambhav.ecommerce.model.Order;
import com.Sambhav.ecommerce.model.OrderItem;
import com.Sambhav.ecommerce.model.Product;
import com.Sambhav.ecommerce.model.User;
import com.Sambhav.ecommerce.repository.CartRepository;
import com.Sambhav.ecommerce.repository.OrderRepository;
import com.Sambhav.ecommerce.repository.PaymentRepository;
import com.Sambhav.ecommerce.repository.ProductRepository;
import com.Sambhav.ecommerce.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public OrderService(OrderRepository orderRepository, CartRepository cartRepository,
            UserRepository userRepository, ProductRepository productRepository,
            PaymentRepository paymentRepository, EntityManager entityManager,
            PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
        this.paymentService = paymentService;
    }

    @Transactional
    public OrderResponse checkout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        Cart cart = cartRepository.findByUser_IdForUpdate(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot checkout with an empty cart"));
        if (cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot checkout with an empty cart");
        }

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() == null || product.getStockQuantity() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for product: " + product.getName());
            }
        }

        Order order = new Order();
        order.setUser(user);
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            int updatedRows = productRepository.decrementStockIfAvailable(product.getId(), cartItem.getQuantity());
            if (updatedRows == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for product: " + product.getName());
            }
            entityManager.refresh(product);
            BigDecimal unitPrice = new BigDecimal(String.valueOf(product.getPrice()));
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setUnitPrice(unitPrice);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setSubtotal(subtotal);
            order.addItem(orderItem);
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);
        Order savedOrder = orderRepository.save(order);
        cart.getItems().clear();
        return buildOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        return orderRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::buildOrderResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::buildOrderResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String email, Integer orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        Order order = orderRepository.findByIdAndUser_Id(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return buildOrderResponse(order);
    }

    @Transactional
    public CancellationResponse cancelMyOrder(String email, Integer orderId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
        Order order = orderRepository.findByIdAndUser_IdForUpdate(orderId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            PaymentResponse previousPayment = paymentService.findSuccessfulRefundForOrder(email, orderId);
            return new CancellationResponse(buildOrderResponse(order), previousPayment, true, "Order was already cancelled.");
        }
        if (order.getStatus() != OrderStatus.PLACED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only placed or confirmed orders can be cancelled. Shipped and delivered orders cannot be cancelled here.");
        }

        PaymentResponse refund = paymentService.refundSuccessfulPaymentIfPresent(email, orderId);
        if (refund != null && !"SUCCESS".equals(refund.getRefundStatus())) {
            return new CancellationResponse(buildOrderResponse(order), refund, false,
                    "The simulated refund failed. The order remains active and stock was not restored; try again.");
        }

        restoreStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        String message = refund == null
                ? "Order cancelled. Reserved stock has been restored."
                : "Order cancelled and simulated refund completed.";
        return new CancellationResponse(buildOrderResponse(saved), refund, true, message);
    }

    @Transactional
    public OrderResponse updateOrderStatus(Integer orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();
        boolean validTransition = switch (currentStatus) {
            case PLACED -> newStatus == OrderStatus.CONFIRMED || newStatus == OrderStatus.CANCELLED;
            case CONFIRMED -> newStatus == OrderStatus.SHIPPED;
            case SHIPPED -> newStatus == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!validTransition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid order status transition");
        }
        if (newStatus == OrderStatus.CONFIRMED
                && !paymentRepository.existsByOrder_IdAndStatus(orderId, PaymentStatus.SUCCESS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot confirm an order without successful payment");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            if (paymentRepository.existsByOrder_IdAndStatus(orderId, PaymentStatus.SUCCESS)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Use the customer cancellation and refund workflow for an order with a successful payment");
            }
            restoreStock(order);
        }
        order.setStatus(newStatus);
        return buildOrderResponse(orderRepository.save(order));
    }

    private void restoreStock(Order order) {
        for (OrderItem orderItem : order.getItems()) {
            productRepository.incrementStock(orderItem.getProduct().getId(), orderItem.getQuantity());
            entityManager.refresh(orderItem.getProduct());
        }
    }

    private OrderResponse buildOrderResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(order.getId());
        response.setStatus(order.getStatus().name());
        response.setTotalAmount(order.getTotalAmount());
        response.setCreatedAt(order.getCreatedAt());
        if (order.getUser() != null) {
            response.setCustomerName(order.getUser().getName());
            response.setCustomerEmail(order.getUser().getEmail());
        }
        for (OrderItem orderItem : order.getItems()) {
            OrderItemResponse itemResponse = new OrderItemResponse();
            itemResponse.setProductId(orderItem.getProduct().getId());
            itemResponse.setProductName(orderItem.getProductName());
            itemResponse.setUnitPrice(orderItem.getUnitPrice());
            itemResponse.setQuantity(orderItem.getQuantity());
            itemResponse.setSubtotal(orderItem.getSubtotal());
            response.getItems().add(itemResponse);
        }
        return response;
    }
}
