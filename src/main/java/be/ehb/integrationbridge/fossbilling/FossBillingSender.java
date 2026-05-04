package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.EmailMessage;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes an EmailMessage to the send_email queue after successful invoice creation.
 * Uses JSON serialization via ObjectMapper.
 */
@Component
public class FossBillingSender {

    private static final Logger log = LoggerFactory.getLogger(FossBillingSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishEmailMessage(SaleMessage sale, int invoiceId, String invoiceNumber) {
        try {
            // Validate email before publishing
            String email = sale.getCustomer().getEmail();
            if (email == null || !email.contains("@")) {
                log.warn("Skipping email publish for saleId={} — invalid email: {}",
                        sale.getSaleId(), email);
                return;
            }

            // Build InvoiceItems from SaleItems
            List<InvoiceItem> invoiceItems = new ArrayList<>();
            if (sale.getItems() != null) {
                for (SaleItem saleItem : sale.getItems()) {
                    InvoiceItem item = new InvoiceItem();
                    item.setTitle(saleItem.getProduct());
                    item.setQuantity(saleItem.getQuantity());
                    item.setPrice(saleItem.getPriceUnit());
                    invoiceItems.add(item);
                }
            }

            // Build EmailMessage
            EmailMessage emailMessage = new EmailMessage();
            emailMessage.setEventType("INVOICE_CREATED");
            emailMessage.setSource("fossbilling");
            emailMessage.setInvoiceId(invoiceId);
            emailMessage.setInvoiceNumber(invoiceNumber);
            emailMessage.setTimestamp(LocalDate.now().toString());
            emailMessage.setCustomer(sale.getCustomer());
            emailMessage.setItems(invoiceItems);
            emailMessage.setTotal(sale.getAmountTotal());
            emailMessage.setDueAt(LocalDate.now().plusDays(30).toString());

            // Serialize to JSON and publish
            String json = objectMapper.writeValueAsString(emailMessage);
            rabbitTemplate.convertAndSend(RabbitMQConfig.SEND_EMAIL_QUEUE, json);

            log.info("Published EmailMessage to send_email queue: invoiceNumber={}, to={}",
                    invoiceNumber, email);

        } catch (Exception e) {
            log.error("Failed to publish email message for saleId={}: {}",
                    sale.getSaleId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish to send_email queue", e);
        }
    }
}