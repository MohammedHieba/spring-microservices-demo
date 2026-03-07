package com.microservices.inventory_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.inventory_service.entity.Inventory;
import com.microservices.inventory_service.entity.OutboxEvent;
import com.microservices.inventory_service.repository.InventoryRepository;
import com.microservices.inventory_service.repository.OutboxRepository;
import com.microservices.microservicesevents.dto.OrderItem;
import com.microservices.microservicesevents.inventory.InventoryResponseEvent;
import com.microservices.microservicesevents.order.OrderApprovedEvent;
import com.microservices.microservicesevents.order.OrderCreatedEvent;
import com.microservices.microservicesevents.order.OrderRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    final static String AGGREGATE_TYPE = "inventory";
    final static String INVENTORY_RESPONSE_TOPIC = "inventory-response-topic";
    final static String ORDER_CREATED_TOPIC = "order-created-topic";
    final static String ORDER_APPROVED_TOPIC = "order-approved-topic";
    final static String ORDER_REJECTED_TOPIC = "order-rejected-topic";
    final static String INVENTORY_SERVICE_GROUP = "inventory-service";


    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;


    @KafkaListener(topics = ORDER_CREATED_TOPIC, groupId = INVENTORY_SERVICE_GROUP)
    @Transactional
    public void handleOrderCreated(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.info("InventoryResponseEvent received: {}", record.value());
//       throw new RuntimeException("simulate OrderCreatedEvent will not be consumed so it should throw back to DTL");
        boolean success = true;
        List<OrderItem> reservedItems = new ArrayList<>();
        OrderCreatedEvent created = objectMapper.readValue(record.value(), OrderCreatedEvent.class);

        for (OrderItem orderItem : created.getOrderItems()) {
            boolean reserved = this.reserve(orderItem.getSkuCode(), orderItem.getQuantity());
            if (!reserved) {
                success = false;
                break;
            }
            reservedItems.add(orderItem);
        }

        if (!success) {
            reservedItems.forEach(
                    item -> release(item.getSkuCode(), item.getQuantity(), Boolean.FALSE)
            );
        }

        InventoryResponseEvent inventoryResponseEvent =
                new InventoryResponseEvent(created.getOrderNumber(), success);

        saveOutboxEvent(inventoryResponseEvent, INVENTORY_RESPONSE_TOPIC, inventoryResponseEvent.getOrderNumber());
    }

    @KafkaListener(
            topics = ORDER_CREATED_TOPIC + "-dlt",
            groupId = INVENTORY_SERVICE_GROUP
    )
    @Transactional
    public void handleOrderCreatedDLT(ConsumerRecord<String, String> record) throws JsonProcessingException {
        log.error("OrderCreatedEvent moved to DLT: {}", record.value());
        OrderCreatedEvent created = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        // must be compensation (tell OrderService inventory failed) because it's a saga step
        // and the order still in inconsistent state
        InventoryResponseEvent response =
                new InventoryResponseEvent(created.getOrderNumber(), false);
        saveOutboxEvent(response, INVENTORY_RESPONSE_TOPIC, created.getOrderNumber());
        log.error("Compensation event published for order {}", created.getOrderNumber());
    }

    @KafkaListener(topics = ORDER_APPROVED_TOPIC, groupId = INVENTORY_SERVICE_GROUP)
    @Transactional
    public void handleOrderApproved(ConsumerRecord<String, String> record) throws JsonProcessingException {

        OrderApprovedEvent approvedEvent = objectMapper.readValue(record.value(), OrderApprovedEvent.class);

        if(!CollectionUtils.isEmpty(approvedEvent.getOrderItems())){
            approvedEvent.getOrderItems().forEach(
                    item -> this.release(item.getSkuCode(), item.getQuantity(), Boolean.TRUE)
            );
        }
    }


    @KafkaListener(
            topics = ORDER_APPROVED_TOPIC + "-dlt",
            groupId = INVENTORY_SERVICE_GROUP
    )
    public void handleOrderApprovedDLT(ConsumerRecord<String, String> record) {
        log.error("OrderApprovedEvent moved to DLT. Manual intervention required. Payload: {}",
                record.value());

        // send alert
        // or implement manual replay (controlled endpoint) by saving failed events in parking lot topics or dlt or database storing failed events
        // and then produce again to the main topic
        // this make our system reliable meaning there is no lost events
    }


    // this listener will be used if the payment service (will be added in the future)
    // failed the inventory must released using this listener

    @KafkaListener(topics = ORDER_REJECTED_TOPIC, groupId = INVENTORY_SERVICE_GROUP)
    @Transactional
    public void handleOrderRejected(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderRejectedEvent rejectedEvent = objectMapper.readValue(record.value(), OrderRejectedEvent.class);

        if(!CollectionUtils.isEmpty(rejectedEvent.getOrderItems())){
            rejectedEvent.getOrderItems().forEach(
                    item -> this.release(item.getSkuCode(), item.getQuantity(), Boolean.FALSE)
            );
        }

    }


    @KafkaListener(
            topics = ORDER_REJECTED_TOPIC + "-dlt",
            groupId = INVENTORY_SERVICE_GROUP
    )
    public void handleOrderRejectedDLT(ConsumerRecord<String, String> record) {
        log.error("OrderRejectedEvent moved to DLT. Manual intervention required. Payload: {}",
                record.value());

        // send alert
        // or implement manual replay (controlled endpoint) by saving failed events in parking lot topics or dlt or database storing failed events
        // and then produce again to the main topic
        // this make our system reliable meaning there is no lost events
    }

    private boolean reserve(String skuCode, int quantity) {

        Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new RuntimeException("SKU not found"));

        int available = inventory.getQuantity() - inventory.getReservedQuantity();

        // add defensive code for preventing oversealing
        if (available < quantity) {
            log.warn("Not enough stock for SKU {}", skuCode);
            return false;
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() + quantity
        );

        inventoryRepository.save(inventory);

        log.info("Reserved {} units for SKU {}", quantity, skuCode);
        return true;
    }




    private void release(String skuCode, int quantity, boolean isApprovedOrder) {

        Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new RuntimeException("SKU not found"));

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantity
        );

        if(isApprovedOrder)  inventory.setQuantity(inventory.getQuantity() -quantity);

        inventoryRepository.save(inventory);

        log.info("Released {} units for SKU {}", quantity, skuCode);
    }


    private void saveOutboxEvent(Object event, String topic, String aggregateId)
            throws JsonProcessingException {
        String payloadJson = objectMapper.writeValueAsString(event);
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(aggregateId)
                .topic(topic)
                .payload(payloadJson)
                .createdAt(Instant.now())
                .build();
        outboxRepository.save(outboxEvent);
    }

    private <T> T parseEvent(String payload, Class<T> clazz)  throws JsonProcessingException{
        String json = objectMapper.readValue(payload, String.class);
        return objectMapper.readValue(json, clazz);
    }
}
