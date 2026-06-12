package be.ehb.integrationbridge.sendgrid;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.exception.ApiException;
import be.ehb.integrationbridge.exception.XmlSerializationException;
import be.ehb.integrationbridge.shared.XmlUtils;
import be.ehb.integrationbridge.shared.model.EmailMessage;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens on the send_email queue for EmailMessages published by FossBillingSender.
 *
 * For each message:
 *   1. Deserialize XML into EmailMessage
 *   2. Validate customer email
 *   3. Build HTML email content from invoice data
 *   4. Send email via SendGrid API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendGridReceiver {
    private final SendGridApiClient sendGridApiClient;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Main listener — triggered automatically when a message arrives on send_email.
     */
    @RabbitListener(
        queues = RabbitMQConfig.SEND_EMAIL_QUEUE,
        containerFactory = "rabbitListenerContainerFactory"
    )
    public void onSendEmail(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("Received message from send_email queue");
        log.debug("Message body: {}", body);

        try {
            // Step 1: Deserialize JSON into EmailMessage
            EmailMessage queueMessage = XmlUtils.fromXml(body, EmailMessage.class);
            
            if (queueMessage.getCustomer() == null) {
                log.warn("EmailMessage for invoice is missing customer data");
                return;
            }

            int invoiceId = queueMessage.getInvoiceId();
            String invoiceNumber = queueMessage.getInvoiceNumber();

            log.info(
                "Processing invoiceId={}, invoiceNumber={}",
                invoiceId,
                invoiceNumber
            );

            String customerName = queueMessage.getCustomer().getName();
            String customerEmail = queueMessage.getCustomer().getEmail();

            // Step 2: Validate customer email — missing email is an invalid message format
            if (customerEmail == null || customerEmail.isBlank()) {
                log.warn("EmailMessage for invoice {} is missing customer email", invoiceNumber);
                return; // Ack the message but do not process further
            }

            double invoiceTotal = queueMessage.getTotal();
            String invoiceDueAt = queueMessage.getDueAt();

            List<InvoiceItem> invoiceItems = queueMessage.getItems();

            if (invoiceItems == null) {
                log.warn("No invoice items were passed. Skipping on sending this email...");
                return;
            }
            
            // Step 3: Build subject and HTML content
            String subject = "Invoice " + queueMessage.getInvoiceNumber();

            // Step 4: Send email via SendGrid API
            sendGridApiClient.sendMail(customerEmail, customerName, invoiceNumber, subject, invoiceItems, invoiceTotal, invoiceDueAt);

            log.info("Email sent for invoiceId={}, invoiceNumber={}", invoiceId, invoiceNumber);
        } catch (XmlSerializationException | ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("SendGrid send failed: " + e.getMessage(), e);
        }
    }
}
