package be.ehb.integrationbridge.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Queue names (used by Senders and Receivers)
    public static final String NEW_SALES_QUEUE = "new_sales";
    public static final String SEND_EMAIL_QUEUE = "send_email";
    public static final String HEARTBEAT_QUEUE = "heartbeat_queue";

    // Dead Letter Queue names (failed messages go here after 3 retries)
    public static final String NEW_SALES_DLQ = "new_sales.dlq";
    public static final String SEND_EMAIL_DLQ = "send_email.dlq";

    // Dead Letter Exchange (routes failed messages to the correct DLQ)
    public static final String DLX_EXCHANGE = "dlx.exchange";

    // --- Dead Letter Exchange ---

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // --- Dead Letter Queues ---

    @Bean
    public Queue newSalesDlq() {
        return QueueBuilder.durable(NEW_SALES_DLQ).build();
    }

    @Bean
    public Queue sendEmailDlq() {
        return QueueBuilder.durable(SEND_EMAIL_DLQ).build();
    }

    // Bind each DLQ to the dead letter exchange
    @Bean
    public Binding newSalesDlqBinding() {
        return BindingBuilder.bind(newSalesDlq())
                .to(deadLetterExchange())
                .with(NEW_SALES_QUEUE);
    }

    @Bean
    public Binding sendEmailDlqBinding() {
        return BindingBuilder.bind(sendEmailDlq())
                .to(deadLetterExchange())
                .with(SEND_EMAIL_QUEUE);
    }

    // --- Main Queues ---

    @Bean
    public Queue newSalesQueue() {
        return QueueBuilder.durable(NEW_SALES_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", NEW_SALES_QUEUE)
                .build();
    }

    @Bean
    public Queue sendEmailQueue() {
        return QueueBuilder.durable(SEND_EMAIL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SEND_EMAIL_QUEUE)
                .build();
    }

    @Bean
    public Queue heartbeatQueue() {
        return QueueBuilder.durable(HEARTBEAT_QUEUE).build();
    }
}
