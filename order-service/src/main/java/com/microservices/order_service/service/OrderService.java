package com.microservices.order_service.service;

import com.microservices.microservicesevents.dto.OrderItem;
import com.microservices.microservicesevents.inventory.InventoryResponseEvent;
import com.microservices.microservicesevents.order.OrderCreatedEvent;
import com.microservices.microservicesevents.order.OrderRejectedEvent;
import com.microservices.order_service.dto.OrderLineItemsDto;
import com.microservices.order_service.dto.OrderRequest;
import com.microservices.order_service.entity.Order;
import com.microservices.order_service.entity.OrderLineItems;
import com.microservices.order_service.enums.OrderStatus;
import com.microservices.order_service.mappers.OrderMapper;
import com.microservices.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    private final KafkaTemplate<String, OrderCreatedEvent> createdEventKafkaTemplate;
    private final KafkaTemplate<String, OrderRejectedEvent> rejectedEventKafkaTemplate;
    private final OrderMapper mapper;


    @Transactional
    public String placeOrder(OrderRequest orderRequest) {

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(mapper::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);
        order.setStatus(OrderStatus.PENDING);

        orderRepository.save(order);
        List<OrderItem> OrderItems =  orderRequest.getOrderLineItemsDtoList().stream().map(mapper::mapDtoToOrderItem).toList();

        OrderCreatedEvent event = new OrderCreatedEvent( order.getOrderNumber(), OrderItems);

        createdEventKafkaTemplate.send("order-created-topic", order.getOrderNumber(), event)
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
    @KafkaListener(topics = "inventory-response-topic", groupId = "order-service1")
    @Transactional
    public void handleInventoryResponse(ConsumerRecord<String, InventoryResponseEvent> record) {

        log.info("InventoryResponseEvent received");

        Order order = orderRepository.findByOrderNumber(record.value().getOrderNumber())
                .orElseThrow();

        if (record.value().isInStock()) {
            order.setStatus(OrderStatus.APPROVED);
        } else {
            order.setStatus(OrderStatus.REJECTED);
        }

        orderRepository.save(order);

        log.info("Order {} status updated to {}", order.getOrderNumber(), order.getStatus());
    }

    // compensation in case of consuming the tries of InventoryResponseEvent had been exceeded.
    @KafkaListener(topics = "inventory-response-topic-dlt", groupId = "order-service1")
    @Transactional
    public void handleInventoryResponseDLT(ConsumerRecord<String, InventoryResponseEvent> record) {

        log.error("Event moved to DLT for order {}", record.value());

        Optional<Order> optionalOrder =
                orderRepository.findByOrderNumber(record.value().getOrderNumber());

        if (optionalOrder.isEmpty()) {
            return;
        }

        Order order = optionalOrder.get();
        // mandatory check for handling Idempotency
        if (optionalOrder.get().getStatus() != OrderStatus.PENDING) {
            return;
        }
        List<OrderLineItemsDto> orderLineItems = order.getOrderLineItemsList()
                .stream()
                .map(mapper::mapToDto)
                .toList();
        List<OrderItem> OrderItems =  order.getOrderLineItemsList().stream().map(mapper::mapEntityToOrderItem).toList();
        OrderRejectedEvent rejectedEvent = new OrderRejectedEvent(order.getOrderNumber(), OrderItems);

        // Compensation Action
        order.setStatus(OrderStatus.REJECTED);

        orderRepository.save(order);

        log.info("Order {} marked as REJECTED due to Saga failure", order.getOrderNumber());

        rejectedEventKafkaTemplate.send("order-rejected-topic", order.getOrderNumber(), rejectedEvent)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("OrderRejectedEvent sent for order {}", order.getOrderNumber());
                    } else {
                        log.error("Failed to send OrderCreatedEvent for order {}", order.getOrderNumber(), ex);
                    }
                });
    }

}
