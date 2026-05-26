package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.exception.ApiException;
import be.ehb.integrationbridge.exception.XmlSerializationException;
import be.ehb.integrationbridge.shared.XmlUtils;
import be.ehb.integrationbridge.shared.model.EmailMessage;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class FossBillingSender {

    private static final Logger log = LoggerFactory.getLogger(FossBillingSender.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void publishEmailMessage(SaleMessage sale, int invoiceId, String invoiceNumber) {
        if (sale == null) {
            log.warn("SaleMessage is null — skipping email publish");
            return;
        }

        if (sale.getCustomer() == null) {
            log.warn("SaleMessage has no customer for saleId={} — skipping email publish",
                    sale.getSaleId());
            return;
        }

        String email = sale.getCustomer().getEmail();
        if (email == null || !email.contains("@")) {
            log.warn("Skipping email publish for saleId={} — invalid email: {}",
                    sale.getSaleId(), email);
            return;
        }

        try {
            // Build InvoiceItems from SaleItems
            List<InvoiceItem> invoiceItems = new ArrayList<>();
            if (sale.getItems() != null) {
                for (SaleItem saleItem : sale.getItems()) {
                    if (saleItem == null) continue;

                    InvoiceItem item = new InvoiceItem();
                    item.setTitle(saleItem.getProduct() != null
                            ? saleItem.getProduct() : "Unknown product");
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
            emailMessage.setInvoiceNumber(invoiceNumber != null ? invoiceNumber : "UNKNOWN");
            emailMessage.setTimestamp(LocalDate.now().toString());
            emailMessage.setCustomer(sale.getCustomer());
            emailMessage.setItems(invoiceItems);
            emailMessage.setTotal(sale.getAmountTotal());
            emailMessage.setDueAt(LocalDate.now().plusDays(30).toString());

            // Serialize to XML using XmlUtils (JAXB)
            String xml;
            try {
                xml = XmlUtils.toXml(emailMessage);
            } catch (Exception e) {
                throw new XmlSerializationException(
                        "Failed to serialize EmailMessage to XML: " + e.getMessage(), e);
            }

            // Use 2-argument convertAndSend(String, Object) — no ambiguity
            rabbitTemplate.convertAndSend(RabbitMQConfig.SEND_EMAIL_QUEUE, xml);

            log.info("Published XML EmailMessage to send_email queue: invoiceNumber={}, to={}",
                    invoiceNumber, email);

        } catch (XmlSerializationException | ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to publish email message for saleId={}: {}",
                    sale.getSaleId(), e.getMessage(), e);
            throw new ApiException(
                    "Failed to publish to send_email queue: " + e.getMessage(), e);
        }
    }
}