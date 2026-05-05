package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens on the new_sales queue for XML sale messages published by OdooSender.
 * Uses XmlMapper (Jackson) for XML deserialization.
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

    private final XmlMapper xmlMapper = new XmlMapper();

    @RabbitListener(queues = RabbitMQConfig.NEW_SALES_QUEUE)
    public void onNewSale(Message message) {
        // Null safety: check message and body
        if (message == null || message.getBody() == null) {
            log.warn("Received null message or empty body — skipping");
            return;
        }

        String body = new String(message.getBody());

        if (body.isBlank()) {
            log.warn("Received blank message body — skipping");
            return;
        }

        log.info("Received message from new_sales queue");
        log.debug("Message body: {}", body);

        int retryCount = getRetryCount(message);

        try {
            // Step 1: Parse XML into SaleMessage using XmlMapper
            SaleMessage sale = xmlMapper.readValue(body, SaleMessage.class);

            // Null safety: check parsed sale object
            if (sale == null) {
                log.warn("Parsed SaleMessage is null — skipping");
                return;
            }

            log.info("Processing saleId={}, posReference={}, customer={}",
                    sale.getSaleId(),
                    sale.getPosReference() != null ? sale.getPosReference() : "N/A",
                    sale.getCustomer() != null ? sale.getCustomer().getEmail() : "null");

            // Step 2: Null safety — skip anonymous sales
            if (sale.getCustomer() == null) {
                log.warn("Sale {} has no customer — skipping (anonymous sale)", sale.getSaleId());
                return;
            }

            String email = sale.getCustomer().getEmail();
            if (email == null || email.isBlank()) {
                log.warn("Sale {} has no customer email — skipping (anonymous sale)",
                        sale.getSaleId());
                return;
            }

            // Null safety: check items list
            if (sale.getItems() == null || sale.getItems().isEmpty()) {
                log.warn("Sale {} has no items — skipping", sale.getSaleId());
                return;
            }

            // Step 3: Find or create customer in FossBilling
            Integer clientId = apiClient.findOrCreateClient(sale.getCustomer());
            if (clientId == null) {
                throw new RuntimeException("Failed to find or create FossBilling client for: "
                        + email);
            }
            log.info("Using FossBilling clientId={} for saleId={}", clientId, sale.getSaleId());

            // Step 4: Create draft invoice
            Integer invoiceId = apiClient.createInvoice(clientId, sale);
            if (invoiceId == null) {
                throw new RuntimeException("Failed to create invoice for clientId: " + clientId);
            }

            // Step 5: Add all items to the invoice
            apiClient.addAllItems(invoiceId, sale.getItems());

            // Step 6: Approve the invoice
            apiClient.approveInvoice(invoiceId);

            // Step 7: Fetch the finalized invoice to get the invoice number
            Map<String, Object> invoiceData = apiClient.getInvoice(invoiceId);
            if (invoiceData == null) {
                throw new RuntimeException("Failed to retrieve invoice data for invoiceId: "
                        + invoiceId);
            }

            // Null safety: check invoice number
            Object nrObj = invoiceData.get("nr");
            String invoiceNumber = nrObj != null ? String.valueOf(nrObj) : "UNKNOWN";

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
            log.error("Message exceeded max retries ({}). Moving to Dead Letter Queue.",
                    MAX_RETRIES);
            throw new RuntimeException("Max retries exceeded: " + e.getMessage(), e);
        }
    }

    private int getRetryCount(Message message) {
        if (message.getMessageProperties() == null
                || message.getMessageProperties().getHeaders() == null) {
            return 0;
        }
        Object retryHeader = message.getMessageProperties().getHeaders().get("x-retry-count");
        return retryHeader instanceof Integer ? (Integer) retryHeader : 0;
    }
}