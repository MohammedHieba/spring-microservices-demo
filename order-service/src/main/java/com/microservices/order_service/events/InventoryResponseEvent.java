package com.microservices.order_service.events;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryResponseEvent {
    private String orderNumber;
    private boolean inStock;
}
