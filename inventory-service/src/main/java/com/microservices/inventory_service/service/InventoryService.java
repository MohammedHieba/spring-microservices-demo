package com.microservices.inventory_service.service;

import com.microservices.inventory_service.entity.Inventory;
import com.microservices.inventory_service.repository.InventoryRepository;
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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, InventoryResponseEvent> kafkaTemplate;

    @KafkaListener(topics = "order-created-topic", groupId = "inventory-service")
    @Transactional
    public void handleOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record) {
        boolean success = true;
        List<OrderItem> reservedItems = new ArrayList<>();
        for (OrderItem orderItem : record.value().getOrderItems()) {
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


        InventoryResponseEvent response =
                new InventoryResponseEvent(record.value().getOrderNumber(), success);

        kafkaTemplate.send("inventory-response-topic",
                record.value().getOrderNumber(),
                response);
    }

    @KafkaListener(topics = "order-approved-topic", groupId = "inventory-service")
    @Transactional
    public void handleOrderApproved(ConsumerRecord<String, OrderApprovedEvent> record) {
        if(!CollectionUtils.isEmpty(record.value().getOrderItems())){
            record.value().getOrderItems().forEach(
                    item -> this.release(item.getSkuCode(), item.getQuantity(), Boolean.TRUE)
            );
        }
    }


    @KafkaListener(topics = "order-rejected-topic", groupId = "inventory-service")
    @Transactional
    public void handleOrderRejected(ConsumerRecord<String, OrderRejectedEvent> record){
        if(!CollectionUtils.isEmpty(record.value().getOrderItems())){
            record.value().getOrderItems().forEach(
                    item -> this.release(item.getSkuCode(), item.getQuantity(), Boolean.FALSE)
            );
        }

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
}
