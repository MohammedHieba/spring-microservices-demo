package com.microservices.order_service.service;

import com.microservices.order_service.dto.OrderLineItemsDto;
import com.microservices.order_service.dto.OrderRequest;
import com.microservices.order_service.entity.Order;
import com.microservices.order_service.entity.OrderLineItems;
import com.microservices.order_service.enums.OrderStatus;
import com.microservices.order_service.events.InventoryResponseEvent;
import com.microservices.order_service.events.OrderCreatedEvent;
import com.microservices.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;


    @Transactional
    public String placeOrder(OrderRequest orderRequest) {

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);
        order.setStatus(OrderStatus.PENDING);

        orderRepository.save(order);

        List<String> skuCodes = orderLineItems.stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getOrderNumber(),
                skuCodes
        );

        kafkaTemplate.send("order-created-topic", order.getOrderNumber(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("OrderCreatedEvent sent for order {}", order.getOrderNumber());
                    } else {
                        log.error("Failed to send OrderCreatedEvent for order {}", order.getOrderNumber(), ex);
                    }
                });

        return "Order placed and waiting for confirmation";

    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "inventory-response-topic", groupId = "order-service")
    @Transactional
    public void handleInventoryResponse(InventoryResponseEvent event) {

        Order order = orderRepository.findByOrderNumber(event.getOrderNumber())
                .orElseThrow();

        if (event.isInStock()) {
            order.setStatus(OrderStatus.APPROVED);
        } else {
            order.setStatus(OrderStatus.REJECTED);
        }

        orderRepository.save(order);

        log.info("Order {} status updated to {}", order.getOrderNumber(), order.getStatus());
    }

    @KafkaListener(topics = "inventory-response-topic-dlt", groupId = "order-service")
    @Transactional
    public void handleInventoryResponseDLT(InventoryResponseEvent event) {

        log.error("Event moved to DLT for order {}", event.getOrderNumber());

        Optional<Order> optionalOrder =
                orderRepository.findByOrderNumber(event.getOrderNumber());

        if (optionalOrder.isEmpty()) {
            return;
        }

        Order order = optionalOrder.get();
        // mandatory check for handling Idempotency
        if (optionalOrder.get().getStatus() != OrderStatus.PENDING) {
            return;
        }

        // Compensation Action
        order.setStatus(OrderStatus.REJECTED);

        orderRepository.save(order);

        log.info("Order {} marked as REJECTED due to Saga failure", order.getOrderNumber());
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
