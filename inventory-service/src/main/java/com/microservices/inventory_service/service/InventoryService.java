package com.microservices.inventory_service.service;

import com.microservices.inventory_service.dto.OrderLineItemsDto;
import com.microservices.inventory_service.entity.Inventory;
import com.microservices.inventory_service.events.InventoryResponseEvent;
import com.microservices.inventory_service.events.OrderCreatedEvent;
import com.microservices.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public void handleOrderCreated(OrderCreatedEvent event) {
        boolean success = true;
        List<OrderLineItemsDto> reservedItems = new ArrayList<>();
        for (OrderLineItemsDto orderItem : event.getOrderLineItemsDtoList()) {
            boolean reserved = this.reserve(orderItem.getSkuCode(), orderItem.getQuantity());
            if (!reserved) {
                success = false;
                break;
            }
            reservedItems.add(orderItem);
        }

        if (!success) {
            reservedItems.forEach(
                    item -> release(item.getSkuCode(), item.getQuantity())
            );
        }


        InventoryResponseEvent response =
                new InventoryResponseEvent(event.getOrderNumber(), success);

        kafkaTemplate.send("inventory-response-topic",
                event.getOrderNumber(),
                response);
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


    //-------------------------------------------------event consumer in case of failure of order has been rejected
    @KafkaListener(topics = "order-rejected-topic", groupId = "inventory-service")
    @Transactional
    public void handleOrderRejected(OrderCreatedEvent event){

        if(!CollectionUtils.isEmpty(event.getOrderLineItemsDtoList())){
            event.getOrderLineItemsDtoList().forEach(
                    item -> this.release(item.getSkuCode(), item.getQuantity())
            );
        }

    }

    private void release(String skuCode, int quantity) {

        Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new RuntimeException("SKU not found"));

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantity
        );

        inventoryRepository.save(inventory);

        log.info("Released {} units for SKU {}", quantity, skuCode);
    }
}
