package com.turkcell.order_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.order_service.client.CustomerClient;
import com.turkcell.order_service.client.ProductClient;
import com.turkcell.order_service.client.dto.CustomerResponse;
import com.turkcell.order_service.client.dto.CustomerStatus;
import com.turkcell.order_service.client.dto.ProductResponse;
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
import com.turkcell.order_service.kafka.entity.ProcessedEvent;
import com.turkcell.order_service.kafka.event.PaymentCompletedEvent;
import com.turkcell.order_service.kafka.event.PaymentFailedEvent;
import com.turkcell.order_service.kafka.event.SubscriptionActivatedEvent;
import com.turkcell.order_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.order_service.outbox.entity.OutboxEvent;
import com.turkcell.order_service.outbox.enums.OutboxStatus;
import com.turkcell.order_service.outbox.event.OrderCancelledEvent;
import com.turkcell.order_service.outbox.event.OrderConfirmedEvent;
import com.turkcell.order_service.outbox.event.OrderCreatedEvent;
import com.turkcell.order_service.outbox.event.OrderCreatedItemEvent;
import com.turkcell.order_service.outbox.repository.OutboxEventRepository;
import com.turkcell.order_service.repository.OrderItemRepository;
import com.turkcell.order_service.repository.OrderRepository;
import com.turkcell.order_service.repository.SagaStateRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SagaStateRepository sagaStateRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SagaStateRepository sagaStateRepository,
            CustomerClient customerClient,
            ProductClient productClient,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            ProcessedEventRepository processedEventRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.customerClient = customerClient;
        this.productClient = productClient;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        validateCreateOrderRequest(request);
        validateCustomer(request.customerId());

        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setTotalAmount(BigDecimal.ZERO);
        order.setCurrency("TRY");
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = request.items()
            .stream()
            .map(itemRequest -> createOrderItem(savedOrder.getId(), itemRequest))
            .toList();

        BigDecimal totalAmount = calculateTotalAmount(orderItems);
        savedOrder.setTotalAmount(totalAmount);
        orderRepository.save(savedOrder);

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        SagaState sagaState = new SagaState();
        sagaState.setOrderId(savedOrder.getId());
        sagaState.setStatus(SagaStatus.PAYMENT_PENDING);
        sagaStateRepository.save(sagaState);

        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent(
            UUID.randomUUID(),
            "OrderCreated",
            savedOrder.getId(),
            savedOrder.getCustomerId(),
            savedOrder.getTotalAmount(),
            savedOrder.getCurrency(),
            savedOrderItems.stream()
                .map(item -> new OrderCreatedItemEvent(
                    item.getProductId(),
                    item.getProductCode(),
                    item.getProductType(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal(),
                    item.getMinutesIncluded(),
                    item.getSmsIncluded(),
                    item.getDataMbIncluded()))
                .toList(),
            LocalDateTime.now());

        saveOutboxEvent(savedOrder.getId(), "OrderCreated", orderCreatedEvent);

        return toOrderResponse(savedOrder, savedOrderItems);
    }

    // Operasyon konsolu: tum siparisler (admin listesi, en yeni ustte).
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(o -> toOrderResponse(o, orderItemRepository.findByOrderId(o.getId())))
                .toList();
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
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            return toOrderResponse(order, items);
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

        saveOrderCancelledOutboxEvent(savedOrder, "Order cancelled by request.");

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());

        return toOrderResponse(savedOrder, items);
    }

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Event {} already processed, skipping.", event.eventId());
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.error("Order not found for id: {}, skipping PaymentCompleted event.", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is already CANCELLED, skipping PaymentCompleted.", event.orderId());
            saveProcessedEvent(event.eventId(), event.eventType());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.setStatus(SagaStatus.PAYMENT_COMPLETED);
            sagaStateRepository.save(saga);
        });

        OrderConfirmedEvent confirmedEvent = new OrderConfirmedEvent(
            UUID.randomUUID(), "OrderConfirmed",
            order.getId(), order.getCustomerId(),
            order.getTotalAmount(), order.getCurrency(),
            LocalDateTime.now());
        saveOutboxEvent(order.getId(), "OrderConfirmed", confirmedEvent);

        saveProcessedEvent(event.eventId(), event.eventType());

        log.info("Order {} marked as PAID for payment {}.", order.getId(), event.paymentId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Event {} already processed, skipping.", event.eventId());
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.error("Order not found for id: {}, skipping PaymentFailed event.", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.FULFILLED) {
            log.warn("Order {} is already FULFILLED, skipping PaymentFailed.", event.orderId());
            saveProcessedEvent(event.eventId(), event.eventType());
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is already CANCELLED, skipping PaymentFailed.", event.orderId());
            saveProcessedEvent(event.eventId(), event.eventType());
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setLastError(event.reason());
            sagaStateRepository.save(saga);
        });

        saveProcessedEvent(event.eventId(), event.eventType());

        saveOrderCancelledOutboxEvent(order, event.reason());

        log.info("Order {} marked as CANCELLED due to payment failure: {}", order.getId(), event.reason());
    }

    @Transactional
    public void handleSubscriptionActivated(SubscriptionActivatedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Event {} already processed, skipping.", event.eventId());
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            log.error("Order not found for id: {}, skipping SubscriptionActivated event.", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is already CANCELLED, skipping SubscriptionActivated.", event.orderId());
            saveProcessedEvent(event.eventId(), event.eventType());
            return;
        }

        if (order.getStatus() == OrderStatus.FULFILLED) {
            log.warn("Order {} is already FULFILLED, skipping SubscriptionActivated.", event.orderId());
            saveProcessedEvent(event.eventId(), event.eventType());
            return;
        }

        order.setStatus(OrderStatus.FULFILLED);
        orderRepository.save(order);

        sagaStateRepository.findByOrderId(order.getId()).ifPresent(saga -> {
            saga.setStatus(SagaStatus.COMPLETED);
            sagaStateRepository.save(saga);
        });

        saveProcessedEvent(event.eventId(), event.eventType());

        log.info("Order {} marked as FULFILLED for subscription {}.", order.getId(), event.subscriptionId());
    }

    private void saveProcessedEvent(UUID eventId, String eventType) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventId(eventId);
        processedEvent.setEventType(eventType);
        processedEventRepository.save(processedEvent);
    }

    private void saveOutboxEvent(UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + eventType + " event", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setAggregateType("ORDER");
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(json);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    private void saveOrderCancelledOutboxEvent(Order order, String reason) {
        OrderCancelledEvent cancelledEvent = new OrderCancelledEvent(
                UUID.randomUUID(),
                "OrderCancelled",
                order.getId(),
                order.getCustomerId(),
                reason,
                LocalDateTime.now());

        saveOutboxEvent(order.getId(), "OrderCancelled", cancelledEvent);
    }

    private void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }

        for (CreateOrderItemRequest item : request.items()) {
            if (item.productCode() == null || item.productCode().isBlank()) {
                throw new IllegalArgumentException("Item productCode is required.");
            }

            if (item.productType() == null) {
                throw new IllegalArgumentException("Item productType is required.");
            }

            if (item.quantity() == null || item.quantity() < 1) {
                throw new IllegalArgumentException("Item quantity must be greater than zero.");
            }

            // unitPrice may be null in new flow; if present it must be > 0
            if (item.unitPrice() != null && item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Item unit price must be greater than zero when provided.");
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

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice() != null
                        ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private OrderItem createOrderItem(UUID orderId, CreateOrderItemRequest request) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(orderId);
        orderItem.setProductId(request.productId());
        orderItem.setProductName(request.productName());
        orderItem.setProductCode(request.productCode());
        orderItem.setProductType(request.productType());
        orderItem.setQuantity(request.quantity());

        if (request.productType() == com.turkcell.order_service.enums.OrderProductType.TARIFF) {
            try {
                ProductResponse product = productClient.getTariffByCode(request.productCode());
                if (product == null) {
                    throw new IllegalStateException("Product not found in catalog with code: " + request.productCode());
                }
                if (!"ACTIVE".equalsIgnoreCase(product.status())) {
                    throw new IllegalStateException("Product not active in catalog with code: " + request.productCode());
                }
                if (product.monthlyFee() == null) {
                    throw new IllegalStateException("Product Catalog missing price for code: " + request.productCode());
                }

                BigDecimal unitPrice = product.monthlyFee();
                orderItem.setProductName(product.name() != null ? product.name() : request.productName());
                orderItem.setUnitPrice(unitPrice);
                orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
                orderItem.setMinutesIncluded(product.minutesIncluded());
                orderItem.setSmsIncluded(product.smsIncluded());
                orderItem.setDataMbIncluded(product.dataMbIncluded());
            } catch (FeignException.NotFound e) {
                log.error("Tariff {} not found in Product Catalog.", request.productCode(), e);
                throw new IllegalStateException("Product not found in catalog with code: " + request.productCode(), e);
            } catch (FeignException e) {
                log.error("Product Catalog unavailable for tariff {}.", request.productCode(), e);
                throw new IllegalStateException("Product Catalog unavailable for code: " + request.productCode(), e);
            }
        } else if (request.productType() == com.turkcell.order_service.enums.OrderProductType.ADDON) {
            try {
                ProductResponse product = productClient.getAddonByCode(request.productCode());
                if (product == null) {
                    throw new IllegalStateException("Product not found in catalog with code: " + request.productCode());
                }
                if (product.price() == null) {
                    throw new IllegalStateException("Product Catalog missing price for code: " + request.productCode());
                }

                BigDecimal unitPrice = product.price();
                orderItem.setProductName(product.name() != null ? product.name() : request.productName());
                orderItem.setUnitPrice(unitPrice);
                orderItem.setLineTotal(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
            } catch (FeignException.NotFound e) {
                log.error("Addon {} not found in Product Catalog.", request.productCode(), e);
                throw new IllegalStateException("Product not found in catalog with code: " + request.productCode(), e);
            } catch (FeignException e) {
                log.error("Product Catalog unavailable for addon {}.", request.productCode(), e);
                throw new IllegalStateException("Product Catalog unavailable for code: " + request.productCode(), e);
            }
        }

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
            item.getProductCode(),
            item.getProductType(),
            item.getProductName(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getLineTotal());
    }
}
