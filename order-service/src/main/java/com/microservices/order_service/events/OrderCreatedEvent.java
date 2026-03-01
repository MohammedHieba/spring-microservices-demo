package com.microservices.order_service.events;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {
    private String orderNumber;
    private List<String> skuCodes;
}
