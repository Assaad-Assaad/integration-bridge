package com.integration.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String NEW_SALES_QUEUE = "new_sales";
    public static final String HEARTBEAT_QUEUE = "heartbeat_queue"; // <--- Add this

    @Bean
    public Queue newSalesQueue() {
        return new Queue(NEW_SALES_QUEUE, true);
    }

    @Bean
    public Queue heartbeatQueue() { // <--- Add this
        return new Queue(HEARTBEAT_QUEUE, true);
    }
}