package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.exception.ApiException;
import be.ehb.integrationbridge.exception.XmlSerializationException;
import be.ehb.integrationbridge.shared.XmlUtils;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FossBillingReceiver {

    private final FossBillingApiClient apiClient;
    private final FossBillingSender sender;

    // Fix: explicit containerFactory so Spring uses the retry config
    // (without it, Spring uses the default factory and every failure goes straight to DLQ)
    @RabbitListener(
            queues = RabbitMQConfig.NEW_SALES_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void onNewSale(Message message) {
        if (message == null || message.getBody() == null) {
            log.warn("Received null message or empty body — skipping");
            return;
        }

        // UTF-8 fix: explicitly decode body as UTF-8
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        if (body.isBlank()) {
            log.warn("Received blank message body — skipping");
            return;
        }

        log.info("Received message from new_sales queue");
        log.debug("Message body: {}", body);

        try {
            // Step 1: Parse XML into SaleMessage using XmlUtils (JAXB)
            SaleMessage sale;
            try {
                sale = XmlUtils.fromXml(body, SaleMessage.class);
            } catch (Exception e) {
                throw new XmlSerializationException(
                        "Failed to parse SaleMessage XML: " + e.getMessage(), e);
            }

            if (sale == null) {
                log.warn("Parsed SaleMessage is null — skipping");
                return;
            }

            log.info("Processing saleId={}, posReference={}, customer={}",
                    sale.getSaleId(),
                    sale.getPosReference() != null ? sale.getPosReference() : "N/A",
                    sale.getCustomer() != null ? sale.getCustomer().getEmail() : "null");

            // Step 2: Skip anonymous sales
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

            if (sale.getItems() == null || sale.getItems().isEmpty()) {
                log.warn("Sale {} has no items — skipping", sale.getSaleId());
                return;
            }

            // Step 3: Find or create customer in FossBilling
            Integer clientId = apiClient.findOrCreateClient(sale.getCustomer());
            if (clientId == null) {
                throw new ApiException(
                        "Failed to find or create FossBilling client for: " + email);
            }
            log.info("Using FossBilling clientId={} for saleId={}", clientId, sale.getSaleId());

            // Step 4: Create draft invoice
            Integer invoiceId = apiClient.createInvoice(clientId, sale);
            if (invoiceId == null) {
                throw new ApiException("Failed to create invoice for clientId: " + clientId);
            }

            // Step 5: Add all items
            apiClient.addAllItems(invoiceId, sale.getItems());

            // Step 6: Approve invoice
            apiClient.approveInvoice(invoiceId);

            // Step 7: Fetch invoice number
            Map<String, Object> invoiceData = apiClient.getInvoice(invoiceId);
            if (invoiceData == null || invoiceData.isEmpty()) {
                throw new ApiException(
                        "Failed to retrieve invoice data for invoiceId: " + invoiceId);
            }

            Object nrObj = invoiceData.get("nr");
            String invoiceNumber = nrObj != null ? String.valueOf(nrObj) : "UNKNOWN";

            log.info("Invoice created and approved: invoiceId={}, invoiceNumber={}",
                    invoiceId, invoiceNumber);

            // Step 8: Publish to send_email queue
            sender.publishEmailMessage(sale, invoiceId, invoiceNumber);

        } catch (ApiException | XmlSerializationException e) {
            // Typed exceptions — rethrow so Spring AMQP retries → DLQ after max attempts
            log.error("Error processing sale message: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing sale message: {}", e.getMessage(), e);
            throw new ApiException("Unexpected error: " + e.getMessage(), e);
        }
    }
}