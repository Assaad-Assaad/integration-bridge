package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens on the new_sales queue for sale messages published by OdooSender.
 * Uses JSON deserialization via ObjectMapper.
 */
@Component
public class FossBillingReceiver {

    private static final Logger log = LoggerFactory.getLogger(FossBillingReceiver.class);
    private static final int MAX_RETRIES = 3;

    @Autowired
    private FossBillingApiClient apiClient;

    @Autowired
    private FossBillingSender sender;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMQConfig.NEW_SALES_QUEUE)
    public void onNewSale(Message message) {
        String body = new String(message.getBody());
        log.info("Received message from new_sales queue");
        log.debug("Message body: {}", body);

        int retryCount = getRetryCount(message);

        try {
            // Step 1: Parse JSON into SaleMessage
            SaleMessage sale = objectMapper.readValue(body, SaleMessage.class);
            log.info("Processing saleId={}, customer={}",
                    sale.getSaleId(),
                    sale.getCustomer() != null ? sale.getCustomer().getEmail() : "null");

            // Step 2: Skip anonymous sales
            if (sale.getCustomer() == null
                    || sale.getCustomer().getEmail() == null
                    || sale.getCustomer().getEmail().isBlank()) {
                log.warn("Sale {} has no customer email — skipping (anonymous sale)",
                        sale.getSaleId());
                return;
            }

            // Step 3: Find or create customer in FossBilling
            Integer clientId = apiClient.findOrCreateClient(sale.getCustomer());
            log.info("Using FossBilling clientId={} for saleId={}", clientId, sale.getSaleId());

            // Step 4: Create draft invoice
            Integer invoiceId = apiClient.createInvoice(clientId, sale);

            // Step 5: Add all items
            apiClient.addAllItems(invoiceId, sale.getItems());

            // Step 6: Approve invoice
            apiClient.approveInvoice(invoiceId);

            // Step 7: Get invoice number
            Map<String, Object> invoiceData = apiClient.getInvoice(invoiceId);
            String invoiceNumber = String.valueOf(invoiceData.get("nr"));

            log.info("Invoice created and approved: invoiceId={}, invoiceNumber={}",
                    invoiceId, invoiceNumber);

            // Step 8: Publish to send_email queue
            sender.publishEmailMessage(sale, invoiceId, invoiceNumber);

        } catch (Exception e) {
            log.error("Failed to process sale message (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRIES, e.getMessage(), e);
            handleFailure(message, body, retryCount, e);
        }
    }

    private void handleFailure(Message message, String body, int retryCount, Exception e) {
        if (retryCount < MAX_RETRIES) {
            log.warn("Requeueing message, retry {}/{}", retryCount + 1, MAX_RETRIES);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NEW_SALES_QUEUE,
                    body,
                    msg -> {
                        msg.getMessageProperties().getHeaders()
                                .put("x-retry-count", retryCount + 1);
                        return msg;
                    }
            );
        } else {
            log.error("Message exceeded max retries ({}). Moving to Dead Letter Queue.", MAX_RETRIES);
            throw new RuntimeException("Max retries exceeded: " + e.getMessage(), e);
        }
    }

    private int getRetryCount(Message message) {
        Object retryHeader = message.getMessageProperties().getHeaders().get("x-retry-count");
        return retryHeader instanceof Integer ? (Integer) retryHeader : 0;
    }
}