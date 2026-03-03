package com.microservices.microservicesevents.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class InventoryResponseEvent {
    private String orderNumber;
    private boolean inStock;
}
