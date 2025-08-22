package com.microservices.order_service.service;

import com.microservices.order_service.dto.InventoryResponse;
import com.microservices.order_service.dto.OrderLineItemsDto;
import com.microservices.order_service.dto.OrderRequest;
import com.microservices.order_service.entity.Order;
import com.microservices.order_service.entity.OrderLineItems;
import com.microservices.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();
        List<String> skuCodes = orderLineItems.stream().map(OrderLineItems::getSkuCode).toList();

        order.setOrderLineItemsList(orderLineItems);
        // check stock in the inventory service
        List<InventoryResponse> inventoryResponses = webClientBuilder
                .baseUrl("http://inventory-service")
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/inventory")
                        .queryParam("skuCode", skuCodes)   // multiple values supported
                        .build())
                .attributes(ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId("my-client"))
                .retrieve()
                .bodyToFlux(InventoryResponse.class).collectList().block();

        boolean allProductsInStock = inventoryResponses.stream().allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
        } else {
            throw new RuntimeException("out of stock");
        }

        return "Order Placed";

    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
