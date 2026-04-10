package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all REST API calls to FossBilling.
 *
 * FossBilling API uses:
 *   - HTTP Basic Auth: username = "admin", password = API key
 *   - Form-encoded POST requests
 *   - Base URL: http://fossbilling:80/api/admin/
 *
 * Flow per sale:
 *   1. findClientByEmail()   → check if customer already exists
 *   2. createClient()        → create customer if new
 *   3. createInvoice()       → create draft invoice for client
 *   4. addAllItems()         → add each product line to the invoice
 *   5. approveInvoice()      → finalize the invoice (draft → approved)
 *   6. getInvoice()          → fetch invoice number for the email
 */
@Component
public class FossBillingApiClient {

    private static final Logger log = LoggerFactory.getLogger(FossBillingApiClient.class);

    @Value("${fossbilling.url}")
    private String baseUrl;

    @Value("${fossbilling.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // -------------------------------------------------------------------------
    // CLIENT METHODS
    // -------------------------------------------------------------------------

    /**
     * Search for an existing client by email address.
     * Returns the client ID if found, or null if the customer does not exist yet.
     */
    public Integer findClientByEmail(String email) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("email", email);

            Map<String, Object> response = post("/client/search", params);
            List<Map<String, Object>> list = (List<Map<String, Object>>) response.get("list");

            if (list != null && !list.isEmpty()) {
                Integer clientId = (Integer) list.get(0).get("id");
                log.info("Found existing FossBilling client: id={}, email={}", clientId, email);
                return clientId;
            }

            log.info("No existing client found for email: {}", email);
            return null;

        } catch (Exception e) {
            log.error("Error searching for client by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    /**
     * Create a new client in FossBilling using CustomerInfo.
     * Required fields: email, first_name, last_name, password, country, currency.
     * Returns the new client ID.
     */
    public Integer createClient(CustomerInfo customer) {
        log.info("Creating new FossBilling client for: {}", customer.getEmail());

        // Split the full name into first and last name for FossBilling
        String fullName = customer.getName() != null ? customer.getName() : "";
        String[] nameParts = fullName.trim().split(" ", 2);
        String firstName = nameParts[0];
        String lastName = nameParts.length > 1 ? nameParts[1] : ".";

        // Generate a random password for the FossBilling client account
        // The client won't use this to log in — it's just a required field
        String generatedPassword = "Fb-" + UUID.randomUUID().toString().substring(0, 12) + "!";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("email", customer.getEmail());
        params.add("first_name", firstName);
        params.add("last_name", lastName);
        params.add("password", generatedPassword);
        params.add("country", "BE");   // Belgium — adjust if needed
        params.add("currency", "EUR");

        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            params.add("phone", customer.getPhone());
        }

        Map<String, Object> response = post("/client/create", params);
        Integer clientId = (Integer) response.get("result");

        log.info("Created FossBilling client: id={}", clientId);
        return clientId;
    }

    /**
     * Find existing client or create a new one.
     * Main entry point called by FossBillingReceiver.
     */
    public Integer findOrCreateClient(CustomerInfo customer) {
        Integer clientId = findClientByEmail(customer.getEmail());
        if (clientId == null) {
            clientId = createClient(customer);
        }
        return clientId;
    }

    // -------------------------------------------------------------------------
    // INVOICE METHODS
    // -------------------------------------------------------------------------

    /**
     * Create a draft invoice for the given client.
     * Returns the new invoice ID.
     */
    public Integer createInvoice(Integer clientId, SaleMessage sale) {
        log.info("Creating invoice for clientId={}, saleId={}", clientId, sale.getSaleId());

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", String.valueOf(clientId));
        params.add("currency", "EUR");
        params.add("notes", "Odoo POS Order #" + sale.getSaleId()
                + (sale.getPosReference() != null ? " (" + sale.getPosReference() + ")" : ""));

        Map<String, Object> response = post("/invoice/create", params);
        Integer invoiceId = (Integer) response.get("result");

        log.info("Created draft invoice: id={}", invoiceId);
        return invoiceId;
    }

    /**
     * Add a single InvoiceItem line to an existing invoice.
     */
    public void addInvoiceItem(Integer invoiceId, InvoiceItem item) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("invoice_id", String.valueOf(invoiceId));
        params.add("title", item.getTitle());
        params.add("price", String.valueOf(item.getPrice()));
        params.add("quantity", String.valueOf(item.getQuantity()));

        post("/invoice/item/create", params);
        log.debug("Added item '{}' to invoice {}", item.getTitle(), invoiceId);
    }

    /**
     * Convert all SaleItems and add them to the invoice.
     * SaleItem fields used: product, quantity, priceUnit.
     */
    public void addAllItems(Integer invoiceId, List<SaleItem> saleItems) {
        for (SaleItem saleItem : saleItems) {
            InvoiceItem item = new InvoiceItem();
            item.setTitle(saleItem.getProduct());
            item.setQuantity(saleItem.getQuantity());
            item.setPrice(saleItem.getPriceUnit());
            addInvoiceItem(invoiceId, item);
        }
    }

    /**
     * Approve a draft invoice — changes status from draft to approved.
     */
    public void approveInvoice(Integer invoiceId) {
        log.info("Approving invoice: id={}", invoiceId);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        post("/invoice/approve", params);
        log.info("Invoice approved: id={}", invoiceId);
    }

    /**
     * Fetch full invoice details including the generated invoice number.
     */
    public Map<String, Object> getInvoice(Integer invoiceId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        Map<String, Object> response = post("/invoice/get", params);
        return (Map<String, Object>) response.get("result");
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    /**
     * Build the Basic Auth header: "Basic base64(admin:API_KEY)"
     */
    private String buildBasicAuthHeader() {
        String credentials = "admin:" + apiKey;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Execute a form-encoded POST request to the FossBilling API using Basic Auth.
     */
    private Map<String, Object> post(String endpoint, MultiValueMap<String, String> params) {
        String url = baseUrl + "/api/admin" + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", buildBasicAuthHeader());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("FossBilling API error at " + endpoint
                    + ": HTTP " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();

        if (body != null && body.get("error") != null) {
            throw new RuntimeException("FossBilling API returned error: " + body.get("error"));
        }

        return body;
    }
}