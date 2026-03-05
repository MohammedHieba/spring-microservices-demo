package com.microservices.microservicesevents.order;


import com.microservices.microservicesevents.dto.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {

    private String orderNumber;
    private List<OrderItem> OrderItems;
}
