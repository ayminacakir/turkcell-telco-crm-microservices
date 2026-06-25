package com.turkcell.order_service.service;

import com.turkcell.order_service.client.CustomerClient;
import com.turkcell.order_service.client.dto.CustomerResponse;
import com.turkcell.order_service.client.dto.CustomerStatus;
import com.turkcell.order_service.dto.request.CreateOrderItemRequest;
import com.turkcell.order_service.dto.request.CreateOrderRequest;
import com.turkcell.order_service.dto.response.OrderItemResponse;
import com.turkcell.order_service.dto.response.OrderResponse;
import com.turkcell.order_service.entity.Order;
import com.turkcell.order_service.entity.OrderItem;
import com.turkcell.order_service.entity.SagaState;
import com.turkcell.order_service.enums.OrderStatus;
import com.turkcell.order_service.enums.SagaStatus;
import com.turkcell.order_service.exception.CustomerNotActiveException;
import com.turkcell.order_service.exception.CustomerNotFoundException;
import com.turkcell.order_service.exception.OrderNotFoundException;
import com.turkcell.order_service.repository.OrderItemRepository;
import com.turkcell.order_service.repository.OrderRepository;
import com.turkcell.order_service.repository.SagaStateRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SagaStateRepository sagaStateRepository;
    private final CustomerClient customerClient;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SagaStateRepository sagaStateRepository,
            CustomerClient customerClient) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.customerClient = customerClient;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        validateCreateOrderRequest(request);
        validateCustomer(request.customerId());

        BigDecimal totalAmount = calculateTotalAmount(request.items());

        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setTotalAmount(totalAmount);
        order.setCurrency("TRY");
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = request.items()
                .stream()
                .map(itemRequest -> createOrderItem(savedOrder.getId(), itemRequest))
                .toList();

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        SagaState sagaState = new SagaState();
        sagaState.setOrderId(savedOrder.getId());
        sagaState.setStatus(SagaStatus.PAYMENT_PENDING);
        sagaStateRepository.save(sagaState);

        return toOrderResponse(savedOrder, savedOrderItems);
    }

    public OrderResponse getById(UUID id) {
        Order order = findOrderById(id);
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        return toOrderResponse(order, items);
    }

    @Transactional
    public OrderResponse cancel(UUID id) {
        Order order = findOrderById(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled.");
        }

        if (order.getStatus() == OrderStatus.FULFILLED) {
            throw new IllegalArgumentException("Fulfilled order cannot be cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        SagaState sagaState = sagaStateRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    SagaState newSagaState = new SagaState();
                    newSagaState.setOrderId(order.getId());
                    return newSagaState;
                });

        sagaState.setStatus(SagaStatus.COMPENSATED);
        sagaState.setLastError("Order cancelled by request.");
        sagaStateRepository.save(sagaState);

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        return toOrderResponse(savedOrder, items);
    }

    private void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        for (CreateOrderItemRequest item : request.items()) {
            if (item.quantity() == null || item.quantity() < 1) {
                throw new IllegalArgumentException("Item quantity must be greater than zero.");
            }

            if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Item unit price must be greater than zero.");
            }
        }
    }

    private void validateCustomer(UUID customerId) {
        CustomerResponse customer;

        try {
            customer = customerClient.getById(customerId);
        } catch (FeignException.NotFound exception) {
            throw new CustomerNotFoundException("Customer with id " + customerId + " not found.");
        }

        if (customer.status() != CustomerStatus.ACTIVE) {
            throw new CustomerNotActiveException(
                    "Customer with id " + customerId + " is not active. Current status: " + customer.status());
        }
    }

    private BigDecimal calculateTotalAmount(List<CreateOrderItemRequest> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OrderItem createOrderItem(UUID orderId, CreateOrderItemRequest request) {
        BigDecimal lineTotal = request.unitPrice().multiply(BigDecimal.valueOf(request.quantity()));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(request.productId());
        orderItem.setProductName(request.productName());
        orderItem.setQuantity(request.quantity());
        orderItem.setUnitPrice(request.unitPrice());
        orderItem.setLineTotal(lineTotal);

        return orderItem;
    }

    private Order findOrderById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order with id " + id + " not found."));
    }

    private OrderResponse toOrderResponse(Order order, List<OrderItem> items) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items.stream()
                        .map(this::toOrderItemResponse)
                        .toList());
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}