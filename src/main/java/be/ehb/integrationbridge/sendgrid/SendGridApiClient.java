package be.ehb.integrationbridge.sendgrid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import be.ehb.integrationbridge.exception.ApiException;
import be.ehb.integrationbridge.shared.model.InvoiceItem;

import java.util.List;
import java.util.Map;

/**
 * Handles all REST API calls to SendGrid.
 *
 * SendGrid API uses:
 *   - HTTP Bearer Auth: API key
 *   - Raw body/payload POST requests
 *   - Base URL: https://app.sendgrid.com
 *
 * Flow per invoice:
 *   1. sendMail()   → send an email with invoice details (subject + content) as consumed from the "send_email" queue
 */
@Component
public class SendGridApiClient {
    private static final Logger log = LoggerFactory.getLogger(SendGridApiClient.class);

    @Value("${sendgrid.url}")
    private String baseUrl;

    @Value("${sendgrid.apiKey}")
    private String apiKey;

    @Value("${sendgrid.fromEmail}")
    private String fromEmail;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Calls the /v3/mail/send endpoint.
     *
     * Payload structure:
     * {
     *   "personalizations": [
     *     {
     *         "to": [{"email": <to>}],
     *         "dynamic_template_data": {
     *             "invoiceItems": [],
     *             "invoiceTotal": 25.50,
     *             "invoiceDueAt": "2026-05-31 12:00:00"
     *         }
     *     }
     *   ],
     *   "from": {"email": <from>},
     *   "subject": <subject>,
     *   "template_id": "d-9fb0cc3fdd55479b9771e32b215e7436"
     * }
     */
    public void sendMail(String customerEmail, String subject, List<InvoiceItem> invoiceItems, double invoiceTotal, String invoiceDueAt) {
        Map<String, Object> body = Map.of(
                "personalizations", List.of(
                    Map.of(
                        "to", List.of(Map.of("email", customerEmail)),
                        "dynamic_template_data", Map.of(
                            "invoiceItems", invoiceItems,
                            "invoiceTotal", invoiceTotal,
                            "invoiceDueAt", invoiceDueAt
                        )
                    )
                ),
                "from",    Map.of("email", fromEmail),
                "subject", subject,
                "template_id", "d-9fb0cc3fdd55479b9771e32b215e7436"
        );

        post("/v3/mail/send", body);

        log.debug("Sent email to '{}' with subject '{}'", customerEmail, subject);
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    /**
     * Build the Bearer Auth header: "Bearer {API Key}"
     */
    private String buildBearerAuthHeader() {
        return "Bearer " + apiKey;
    }

    /**
     * Execute a JSON POST request to the SendGrid API using Bearer Auth.
     */
    private void post(String endpoint, Object body) {
        String url = baseUrl + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", buildBearerAuthHeader());

        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ApiException("SendGrid API error at " + endpoint
                    + ": HTTP " + response.getStatusCode());
        }
    }
}