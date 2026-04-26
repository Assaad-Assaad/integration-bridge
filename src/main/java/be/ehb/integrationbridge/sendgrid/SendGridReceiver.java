package be.ehb.integrationbridge.sendgrid;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.XmlUtils;
import be.ehb.integrationbridge.shared.model.EmailMessage;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
// @RequiredArgsConstructor
// @Slf4j
public class SendGridReceiver {
    private static final Logger log = LoggerFactory.getLogger(SendGridReceiver.class);
    private static final int MAX_RETRIES = 3;

    @Autowired
    private SendGridApiClient sendGridApiClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Main listener — triggered automatically when a message arrives on send_email.
     */
    @RabbitListener(queues = RabbitMQConfig.SEND_EMAIL_QUEUE)
    public void onSendEmail(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        log.info("Received message from send_email queue");
        log.debug("Message body: {}", body);

        int retryCount = getRetryCount(message);

        try {
            // Step 1: Deserialize JSON into EmailMessage
            EmailMessage queueMessage = XmlUtils.fromXml(body, EmailMessage.class);
            
            if (queueMessage.getCustomer() != null) {
                log.warn("EmailMessage for invoice is missing customer data");
                return;
            }

            String customerName = queueMessage.getCustomer().getName();
            String customerEmail = queueMessage.getCustomer().getEmail();

            int invoiceId = queueMessage.getInvoiceId();
            String invoiceNumber = queueMessage.getInvoiceNumber();
            double invoiceTotal = queueMessage.getTotal();
            String invoiceTimestamp = queueMessage.getTimestamp();
            String invoiceDueAt = queueMessage.getDueAt();

            List<InvoiceItem> invoiceItems = queueMessage.getItems();

            log.info(
                "Processing invoiceId={}, invoiceNumber={}, customerEmail={}",
                invoiceId,
                invoiceNumber,
                customerEmail
            );

            // Step 2: Validate customer email — missing email is an invalid message format
            if (customerEmail == null || customerEmail.isBlank()) {
                log.warn("EmailMessage for invoice {} is missing customer email", invoiceNumber);
                return; // Ack the message but do not process further
            }

            // Step 3: Build subject and HTML content
            String subject = "Invoice " + queueMessage.getInvoiceNumber();

            // Step 4: Send email via SendGrid API
            sendGridApiClient.sendMail(customerEmail, subject, invoiceItems, invoiceTotal, invoiceDueAt);

            log.info("Email sent for invoiceId={}, invoiceNumber={}", invoiceId, invoiceNumber);
        } catch (Exception e) {
            log.error("Failed to send email (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRIES, e.getMessage(), e);
            handleFailure(message, body, retryCount, e);
        }
    }

    /**
     * On failure: requeue with incremented retry count up to MAX_RETRIES.
     * After that, throw so Spring NACK's the message → RabbitMQ routes to send_email.dlq.
     */
    private void handleFailure(Message message, String body, int retryCount, Exception e) {
        if (retryCount < MAX_RETRIES) {
            log.warn("Requeueing message, retry {}/{}", retryCount + 1, MAX_RETRIES);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SEND_EMAIL_QUEUE,
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