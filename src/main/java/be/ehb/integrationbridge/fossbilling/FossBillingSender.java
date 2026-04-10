package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.EmailMessage;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes an EmailMessage to the send_email queue after a successful invoice creation.
 * The SendGridReceiver will consume this and send the confirmation email to the customer.
 *
 * EmailMessage fields used:
 *   - eventType    → "INVOICE_CREATED"
 *   - source       → "fossbilling"
 *   - invoiceId    → the FossBilling invoice ID (int)
 *   - invoiceNumber→ the human-readable invoice number from FossBilling
 *   - timestamp    → current date
 *   - customer     → CustomerInfo from the original sale
 *   - items        → List<InvoiceItem> built from SaleItems
 *   - total        → amountTotal from the original sale
 *   - dueAt        → payment due date (30 days from today)
 */
@Component
public class FossBillingSender {

    private static final Logger log = LoggerFactory.getLogger(FossBillingSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Build an EmailMessage and publish it to the send_email queue.
     *
     * @param sale          The original SaleMessage from Odoo
     * @param invoiceId     The FossBilling invoice ID (int)
     * @param invoiceNumber The human-readable invoice number from FossBilling
     */
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
                    item.setTitle(saleItem.getProduct());       // SaleItem.product
                    item.setQuantity(saleItem.getQuantity());   // SaleItem.quantity
                    item.setPrice(saleItem.getPriceUnit());     // SaleItem.priceUnit
                    invoiceItems.add(item);
                }
            }

            // Build the EmailMessage matching the exact fields of the model
            EmailMessage emailMessage = new EmailMessage();
            emailMessage.setEventType("INVOICE_CREATED");
            emailMessage.setSource("fossbilling");
            emailMessage.setInvoiceId(invoiceId);
            emailMessage.setInvoiceNumber(invoiceNumber);
            emailMessage.setTimestamp(LocalDate.now().toString());
            emailMessage.setCustomer(sale.getCustomer());      // reuse CustomerInfo object
            emailMessage.setItems(invoiceItems);
            emailMessage.setTotal(sale.getAmountTotal());      // SaleMessage.amountTotal
            emailMessage.setDueAt(LocalDate.now().plusDays(30).toString()); // 30-day payment term

            // Serialize to XML and publish (models use @XmlRootElement)
            String xml = xmlMapper.writeValueAsString(emailMessage);
            rabbitTemplate.convertAndSend(RabbitMQConfig.SEND_EMAIL_QUEUE, xml);

            log.info("Published EmailMessage to send_email queue: invoiceNumber={}, to={}",
                    invoiceNumber, email);

        } catch (Exception e) {
            log.error("Failed to publish email message for saleId={}: {}",
                    sale.getSaleId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish to send_email queue", e);
        }
    }
}