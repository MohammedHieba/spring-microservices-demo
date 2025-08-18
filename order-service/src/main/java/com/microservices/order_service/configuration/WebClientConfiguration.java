package com.microservices.order_service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfiguration {

    @Bean
    public WebClient inventoryWebClient(){
        return WebClient.builder().baseUrl("http://localhost:8081").build();
    }
}
