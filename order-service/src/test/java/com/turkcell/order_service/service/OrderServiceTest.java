package com.turkcell.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkcell.order_service.client.CustomerClient;
import com.turkcell.order_service.client.ProductClient;
import com.turkcell.order_service.client.dto.CustomerResponse;
import com.turkcell.order_service.client.dto.CustomerStatus;
import com.turkcell.order_service.client.dto.ProductResponse;
import com.turkcell.order_service.dto.request.CreateOrderItemRequest;
import com.turkcell.order_service.dto.request.CreateOrderRequest;
import com.turkcell.order_service.dto.response.OrderResponse;
import com.turkcell.order_service.entity.Order;
import com.turkcell.order_service.entity.OrderItem;
import com.turkcell.order_service.entity.SagaState;
import com.turkcell.order_service.enums.OrderProductType;
import com.turkcell.order_service.enums.OrderStatus;
import com.turkcell.order_service.exception.CustomerNotActiveException;
import com.turkcell.order_service.exception.CustomerNotFoundException;
import com.turkcell.order_service.kafka.event.PaymentCompletedEvent;
import com.turkcell.order_service.kafka.event.PaymentFailedEvent;
import com.turkcell.order_service.kafka.repository.ProcessedEventRepository;
import com.turkcell.order_service.outbox.repository.OutboxEventRepository;
import com.turkcell.order_service.repository.OrderItemRepository;
import com.turkcell.order_service.repository.OrderRepository;
import com.turkcell.order_service.repository.SagaStateRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private SagaStateRepository sagaStateRepository;
    @Mock private CustomerClient customerClient;
    @Mock private ProductClient productClient;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                sagaStateRepository,
                customerClient,
                productClient,
                outboxEventRepository,
                new ObjectMapper().findAndRegisterModules(),
                processedEventRepository);
    }

    @Test
    void create_withActiveCustomerAndTariff_savesOrderAndOutboxEvent() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(customerClient.getById(customerId))
                .thenReturn(new CustomerResponse(customerId, CustomerStatus.ACTIVE));
        when(productClient.getTariffByCode("TARIFF_50"))
                .thenReturn(new ProductResponse("TARIFF_50", "50GB Tarife",
                        new BigDecimal("199.99"), null, "ACTIVE", 500, 500, 51200));

        Order savedOrder = buildOrder(orderId, customerId, OrderStatus.PENDING_PAYMENT);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderItem savedItem = buildOrderItem(orderId);
        when(orderItemRepository.saveAll(any())).thenReturn(List.of(savedItem));
        when(sagaStateRepository.save(any())).thenReturn(new SagaState());

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(null, "TARIFF_50", OrderProductType.TARIFF, "50GB Tarife", 1, null)));

        OrderResponse response = orderService.create(request);

        assertThat(response).isNotNull();
        verify(orderRepository, atLeastOnce()).save(any());
        verify(sagaStateRepository).save(any());
        verify(outboxEventRepository).save(any());
    }

    @Test
    void create_withNonexistentCustomer_throwsCustomerNotFoundException() {
        UUID customerId = UUID.randomUUID();
        when(customerClient.getById(customerId))
                .thenThrow(mock(FeignException.NotFound.class));

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(null, "TARIFF_50", OrderProductType.TARIFF, "50GB", 1, null)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(CustomerNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_withPendingCustomer_throwsCustomerNotActiveException() {
        UUID customerId = UUID.randomUUID();
        when(customerClient.getById(customerId))
                .thenReturn(new CustomerResponse(customerId, CustomerStatus.PENDING));

        CreateOrderRequest request = new CreateOrderRequest(
                customerId,
                List.of(new CreateOrderItemRequest(null, "TARIFF_50", OrderProductType.TARIFF, "50GB", 1, null)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(CustomerNotActiveException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_withEmptyItems_throwsIllegalArgumentException() {
        UUID customerId = UUID.randomUUID();

        CreateOrderRequest request = new CreateOrderRequest(customerId, List.of());

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void handlePaymentCompleted_withValidOrder_marksOrderPaidAndPublishesConfirmed() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId, "PaymentCompleted", UUID.randomUUID(), orderId,
                UUID.randomUUID(), new BigDecimal("199.99"), "TRY", "SUCCESS", LocalDateTime.now());

        Order order = buildOrder(orderId, UUID.randomUUID(), OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);
        when(sagaStateRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        orderService.handlePaymentCompleted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(outboxEventRepository).save(any()); // OrderConfirmed event
        verify(processedEventRepository).save(any());
    }

    @Test
    void handlePaymentCompleted_withAlreadyProcessedEvent_skips() {
        UUID eventId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId, "PaymentCompleted", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.TEN, "TRY", "SUCCESS", LocalDateTime.now());

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        orderService.handlePaymentCompleted(event);

        verify(orderRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void handlePaymentFailed_withValidOrder_cancelsOrderAndPublishesCancelled() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PaymentFailedEvent event = new PaymentFailedEvent(
                eventId, "PaymentFailed", UUID.randomUUID(), orderId,
                UUID.randomUUID(), new BigDecimal("199.99"), "TRY", "FAILED", "Insufficient funds", LocalDateTime.now());

        Order order = buildOrder(orderId, UUID.randomUUID(), OrderStatus.PENDING_PAYMENT);
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);
        when(sagaStateRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        orderService.handlePaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxEventRepository).save(any()); // OrderCancelled event
        verify(processedEventRepository).save(any());
    }

    @Test
    void handlePaymentFailed_withAlreadyProcessedEvent_skips() {
        UUID eventId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                eventId, "PaymentFailed", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), BigDecimal.TEN, "TRY", "FAILED", "Timeout", LocalDateTime.now());

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        orderService.handlePaymentFailed(event);

        verify(orderRepository, never()).save(any());
    }

    private Order buildOrder(UUID orderId, UUID customerId, OrderStatus status) {
        Order o = new Order();
        try {
            java.lang.reflect.Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(o, orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        o.setCustomerId(customerId);
        o.setTotalAmount(new BigDecimal("199.99"));
        o.setCurrency("TRY");
        o.prePersist();
        o.setStatus(status);
        return o;
    }

    private OrderItem buildOrderItem(UUID orderId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductCode("TARIFF_50");
        item.setProductName("50GB Tarife");
        item.setProductType(OrderProductType.TARIFF);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("199.99"));
        item.setLineTotal(new BigDecimal("199.99"));
        return item;
    }
}
