package com.microservices.microservicesevents.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponseEvent {
    private String orderNumber;
    private boolean inStock;
}
