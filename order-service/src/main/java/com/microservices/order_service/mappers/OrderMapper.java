package com.microservices.order_service.mappers;

import com.microservices.microservicesevents.dto.OrderItem;
import com.microservices.order_service.dto.OrderLineItemsDto;
import com.microservices.order_service.entity.OrderLineItems;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    public OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

    public OrderLineItemsDto mapToDto(OrderLineItems orderLineItems) {
        OrderLineItemsDto orderLineItemsDto = new OrderLineItemsDto();
        orderLineItemsDto.setPrice(orderLineItemsDto.getPrice());
        orderLineItemsDto.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItemsDto.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItemsDto;
    }

    public OrderItem mapDtoToOrderItem(OrderLineItemsDto orderLineItemsDto) {
        OrderItem item = new OrderItem();
        item.setQuantity(orderLineItemsDto.getQuantity());
        item.setSkuCode(orderLineItemsDto.getSkuCode());

        return item;
    }


    public OrderItem mapEntityToOrderItem(OrderLineItems orderLineItems) {
        OrderItem item = new OrderItem();
        item.setQuantity(orderLineItems.getQuantity());
        item.setSkuCode(orderLineItems.getSkuCode());
        return item;
    }


}
