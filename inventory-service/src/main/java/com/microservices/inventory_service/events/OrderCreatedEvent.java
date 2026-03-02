package com.microservices.inventory_service.events;

import com.microservices.inventory_service.dto.OrderLineItemsDto;
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
   private List<OrderLineItemsDto> orderLineItemsDtoList;
}
