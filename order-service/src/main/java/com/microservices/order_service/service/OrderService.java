package com.microservices.order_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.microservicesevents.dto.OrderItem;
import com.microservices.microservicesevents.inventory.InventoryResponseEvent;
import com.microservices.microservicesevents.order.OrderApprovedEvent;
import com.microservices.microservicesevents.order.OrderCreatedEvent;
import com.microservices.microservicesevents.order.OrderRejectedEvent;
import com.microservices.order_service.dto.OrderLineItemsDto;
import com.microservices.order_service.dto.OrderRequest;
import com.microservices.order_service.entity.Order;
import com.microservices.order_service.entity.OrderLineItems;
import com.microservices.order_service.entity.OutboxEvent;
import com.microservices.order_service.enums.OrderStatus;
import com.microservices.order_service.mappers.OrderMapper;
import com.microservices.order_service.repository.OrderRepository;
import com.microservices.order_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderMapper mapper;
    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    final static String AGGREGATE_TYPE = "order";
    final static String ORDER_CREATED_TOPIC = "order-created-topic";
    final static String ORDER_APPROVED_TOPIC = "order-approved-topic";
    final static String ORDER_REJECTED_TOPIC = "order-rejected-topic";

    // Implement the Outbox Pattern to avoid the dual-write problem by persisting
   // the business data and the corresponding outbox event within the same database
   // transaction, ensuring the system remains in a consistent state.
    @Transactional
    public String placeOrder(OrderRequest orderRequest) throws JsonProcessingException {

        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(mapper::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        orderRepository.save(order);
        List<OrderItem> orderItems =  orderRequest.getOrderLineItemsDtoList().stream().map(mapper::mapDtoToOrderItem).toList();

        OrderCreatedEvent orderCreatedEvent = new OrderCreatedEvent( order.getOrderNumber(), orderItems);

        saveOutboxEvent(orderCreatedEvent, ORDER_CREATED_TOPIC, orderCreatedEvent.getOrderNumber());
        return "Order placed and waiting for confirmation";

    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000),
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "inventory-response-topic", groupId = "order-service")
    @Transactional
    public void handleInventoryResponse(ConsumerRecord<String, String> record) throws JsonProcessingException {

//        throw new RuntimeException("simulate InventoryResponseEvent will not be consumed so it should throw back to DLT for a compensation");
        log.info("InventoryResponseEvent received");
        InventoryResponseEvent inventoryResponseEvent = objectMapper.readValue(record.value(), InventoryResponseEvent.class);

        Order order = orderRepository.findByOrderNumber(inventoryResponseEvent.getOrderNumber())
                .orElseThrow();

        List<OrderItem> OrderItems =  order.getOrderLineItemsList().stream().map(mapper::mapEntityToOrderItem).toList();
        if (inventoryResponseEvent.isInStock()) {
            order.setStatus(OrderStatus.APPROVED);
            OrderApprovedEvent orderApprovedEvent = new OrderApprovedEvent(order.getOrderNumber(), OrderItems);
            saveOutboxEvent(orderApprovedEvent, ORDER_APPROVED_TOPIC, orderApprovedEvent.getOrderNumber());

        } else {
            order.setStatus(OrderStatus.REJECTED);
        }
        orderRepository.save(order);

        log.info("Order {} status updated to {}", order.getOrderNumber(), order.getStatus());
    }

    // compensation in case of consuming the tries of InventoryResponseEvent had been exceeded.
    @KafkaListener(topics = "inventory-response-topic-dlt", groupId = "order-service")
    @Transactional
    public void handleInventoryResponseDLT(ConsumerRecord<String, String> record) throws JsonProcessingException {

        log.error("Event moved to DLT for order {}", record.value());
        String payload = record.value();
        InventoryResponseEvent inventoryResponseEvent = parseEvent(payload, InventoryResponseEvent.class);

        Optional<Order> optionalOrder =
                orderRepository.findByOrderNumber(inventoryResponseEvent.getOrderNumber());

        if (optionalOrder.isEmpty()) {
            return;
        }

        Order order = optionalOrder.get();
        // mandatory check for handling Idempotency
        if (optionalOrder.get().getStatus() != OrderStatus.PENDING) {
            return;
        }

        List<OrderItem> OrderItems =  order.getOrderLineItemsList().stream().map(mapper::mapEntityToOrderItem).toList();
        OrderRejectedEvent rejectedEvent = new OrderRejectedEvent(order.getOrderNumber(), OrderItems);

        // Compensation Action
        order.setStatus(OrderStatus.REJECTED);

        orderRepository.save(order);

        log.info("Order {} marked as REJECTED due to Saga failure", order.getOrderNumber());
        saveOutboxEvent(rejectedEvent, ORDER_REJECTED_TOPIC, rejectedEvent.getOrderNumber());
    }

    private void saveOutboxEvent(Object event, String topic, String aggregateId)
            throws JsonProcessingException {
        JsonNode payload = objectMapper.valueToTree(event);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(aggregateId)
                .topic(topic)
                .payload(payload)
                .createdAt(Instant.now())
                .build();
        outboxRepository.save(outboxEvent);
    }

    private <T> T parseEvent(String payload, Class<T> clazz)  throws JsonProcessingException{
        return objectMapper.readValue(payload, clazz);
    }

}
