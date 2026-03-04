package com.microservices.order_service.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler handler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3));

        handler.setRetryListeners((record, ex, deliveryAttempt) -> {

            log.error("Retry attempt {} for message {}",
                    deliveryAttempt,
                    record.value(),
                    ex);

        });

        return handler;
    }
}