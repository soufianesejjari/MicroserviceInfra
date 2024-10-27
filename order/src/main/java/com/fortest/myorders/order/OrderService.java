package com.fortest.myorders.order;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RefreshScope
public class OrderService {

    private final OrderRepository orderRepository;

    private final RestTemplate restTemplate;

    @Value("${customer.service.url}")
    private String customerServiceUrl;

    @Value("${product.service.url}")
    private String productServiceUrl;

    public OrderService(OrderRepository orderRepository, RestTemplate restTemplate) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
    }

    // Create a new order
    public Order createOrder(OrderRequest orderRequest) {
        // Validate customer existence
        if (!validateCustomerExists(orderRequest.getCustomerId())) {
            throw new IllegalArgumentException("Client non trouvé !");
        }
        // Map OrderItemRequest to OrderItem
        List<OrderItem> orderItems = orderRequest.getOrderItems().stream()
                .map(orderItemRequest -> OrderItem.builder()
                        .order(null)
                        .productId(orderItemRequest.getProductId())
                        .quantity(orderItemRequest.getQuantity())
                        .build())
                .collect(Collectors.toList());

        // Create Order
        Order order = Order.builder()
                .customerId(orderRequest.getCustomerId())
                .orderItems(orderItems)
                .build();

        // Validate product existence and set order reference
        orderItems.forEach(orderItem -> {
            if (!validateProductExists(orderItem.getProductId())) {
                throw new IllegalArgumentException("Produit avec Id " + orderItem.getProductId() + " non trouvé !");
            }
            orderItem.setOrder(order);
        });

        // Save and return the order
        return orderRepository.save(order);
    }

    // Get all orders
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Get a specific order by ID
    public Optional<Order> getOrderById(Integer id) {
        return orderRepository.findById(id);
    }

    // Update an existing order
    public Optional<Order> updateOrder(Integer id, OrderRequest orderRequest) {

        // Validate customer existence
        if (!validateCustomerExists(orderRequest.getCustomerId())) {
            throw new IllegalArgumentException("Client non trouvé !");
        }
        return orderRepository.findById(id).map(order -> {
            // Update orderItems
            List<OrderItem> orderItems = orderRequest.getOrderItems().stream().map(orderItemRequest ->
                    OrderItem.builder()
                            .order(order)
                            .productId(orderItemRequest.getProductId())
                            .quantity(orderItemRequest.getQuantity())
                            .build()
            ).collect(Collectors.toList());
            order.setOrderItems(orderItems);
            order.setCustomerId(orderRequest.getCustomerId());
            return orderRepository.save(order);
        });
    }

    // Delete an order
    public void deleteOrder(Integer id) {
        orderRepository.deleteById(id);
    }



    @CircuitBreaker(name = "customerService", fallbackMethod = "validateCustomerExistsFallback")
    public boolean validateCustomerExists(Integer customerId) {
        String url = customerServiceUrl + "/{id}";
        try {
            ResponseEntity<Void> response = restTemplate.getForEntity(url, Void.class, customerId);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            return false; // Client non trouvé
        }
    }


    @CircuitBreaker(name = "productService", fallbackMethod = "validateProductExistsFallback")
    public boolean validateProductExists(Integer productId) {
        String url = productServiceUrl + "/{id}";
        try {
            ResponseEntity<Void> response = restTemplate.getForEntity(url, Void.class, productId);
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            return false; // Produit non trouvé
        }
    }

    public boolean validateCustomerExistsFallback(Integer customerId, Throwable throwable) {
        log.error("Customer service is not available");
       return false ;
    }

    public boolean validateProductExistsFallback(Integer productId, Throwable throwable) {
        log.error("Product service is not available");
        return false;
    }


}
