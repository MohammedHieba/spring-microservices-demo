package com.microservices.inventory_service.security;

import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
public class SecurityConfig {


    @Bean
    @SneakyThrows
    public SecurityFilterChain SecurityFilterChain(HttpSecurity http) {

        return http.authorizeHttpRequests(auth ->auth.anyRequest().authenticated()).oauth2ResourceServer(
                oauth2 -> oauth2.jwt(Customizer.withDefaults())
        ).oauth2Login(AbstractHttpConfigurer::disable)
                .build();
    }
}
