package com.microservices.inventory_service;

import com.microservices.inventory_service.entity.Inventory;
import com.microservices.inventory_service.repository.InventoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@AllArgsConstructor
public class InventoryServiceApplication implements CommandLineRunner {


    private final InventoryRepository inventoryRepository;

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Inventory entity1 = new Inventory();
        entity1.setSkuCode("iphone_15");
        entity1.setQuantity(10);
        entity1.setReservedQuantity(0);
        entity1.setVersion(0L);

        Inventory entity2 = new Inventory();
        entity2.setSkuCode("iphone_17");
        entity2.setQuantity(20);
        entity2.setReservedQuantity(0);
        entity2.setVersion(0L);

        this.inventoryRepository.save(entity1);
        this.inventoryRepository.save(entity2);
    }
}
