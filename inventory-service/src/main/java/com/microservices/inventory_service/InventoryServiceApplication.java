package com.microservices.inventory_service;

import com.microservices.inventory_service.entity.Inventory;
import com.microservices.inventory_service.repository.InventoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.web.oauth2.resourceserver.OAuth2ResourceServerSecurityMarker;

@SpringBootApplication
@AllArgsConstructor
@OAuth2ResourceServerSecurityMarker
public class InventoryServiceApplication implements CommandLineRunner {


    private final InventoryRepository inventoryRepository;

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Inventory entity1 = new Inventory();
        entity1.setSkuCode("iphone_15");
        entity1.setQuantity(100);

        Inventory entity2 = new Inventory();
        entity2.setSkuCode("iphone_17");
        entity2.setQuantity(1);

        this.inventoryRepository.save(entity1);
        this.inventoryRepository.save(entity2);
    }
}
