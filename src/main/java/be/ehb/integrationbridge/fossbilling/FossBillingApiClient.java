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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all REST API calls to FossBilling.
 *
 * Authentication: HTTP Basic Auth (username = "admin", password = API key)
 * Base URL: http://fossbilling:80/api/admin/
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
        // Null safety: email must be valid
        if (email == null || email.isBlank()) {
            log.warn("findClientByEmail called with null or blank email — returning null");
            return null;
        }

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("email", email);

            Map<String, Object> response = post("/client/search", params);

            // Null safety: check response and list
            if (response == null) {
                log.warn("FossBilling returned null response for client search");
                return null;
            }

            Object listObj = response.get("list");
            if (!(listObj instanceof List)) {
                log.info("No client list in response for email: {}", email);
                return null;
            }

            List<Map<String, Object>> list = (List<Map<String, Object>>) listObj;
            if (list.isEmpty()) {
                log.info("No existing client found for email: {}", email);
                return null;
            }

            // Null safety: check first item and id field
            Map<String, Object> firstClient = list.get(0);
            if (firstClient == null || firstClient.get("id") == null) {
                log.warn("Client found but id is null for email: {}", email);
                return null;
            }

            Integer clientId = (Integer) firstClient.get("id");
            log.info("Found existing FossBilling client: id={}, email={}", clientId, email);
            return clientId;

        } catch (Exception e) {
            log.error("Error searching for client by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    /**
     * Create a new client in FossBilling using CustomerInfo.
     * Returns the new client ID.
     */
    public Integer createClient(CustomerInfo customer) {
        // Null safety: check customer object
        if (customer == null) {
            throw new RuntimeException("Cannot create client: CustomerInfo is null");
        }

        // Null safety: check email
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new RuntimeException("Cannot create client: email is null or blank");
        }

        log.info("Creating new FossBilling client for: {}", customer.getEmail());

        // Null safety: split full name safely
        String fullName = customer.getName() != null ? customer.getName().trim() : "";
        String[] nameParts = fullName.split(" ", 2);
        String firstName = nameParts.length > 0 && !nameParts[0].isBlank()
                ? nameParts[0] : "Unknown";
        String lastName = nameParts.length > 1 && !nameParts[1].isBlank()
                ? nameParts[1] : ".";

        // Generate a random password — required field but client won't use it
        String generatedPassword = "Fb-" + UUID.randomUUID().toString().substring(0, 12) + "!";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("email", customer.getEmail());
        params.add("first_name", firstName);
        params.add("last_name", lastName);
        params.add("password", generatedPassword);
        params.add("country", "BE");
        params.add("currency", "EUR");

        // Null safety: only add phone if present
        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            params.add("phone", customer.getPhone());
        }

        Map<String, Object> response = post("/client/create", params);

        // Null safety: check response and result
        if (response == null || response.get("result") == null) {
            throw new RuntimeException("FossBilling returned null result for client creation");
        }

        Integer clientId = (Integer) response.get("result");
        log.info("Created FossBilling client: id={}", clientId);
        return clientId;
    }

    /**
     * Find existing client or create a new one.
     * Main entry point called by FossBillingReceiver.
     */
    public Integer findOrCreateClient(CustomerInfo customer) {
        // Null safety: check customer
        if (customer == null) {
            throw new RuntimeException("Cannot find or create client: CustomerInfo is null");
        }

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
        // Null safety: check inputs
        if (clientId == null) {
            throw new RuntimeException("Cannot create invoice: clientId is null");
        }
        if (sale == null) {
            throw new RuntimeException("Cannot create invoice: SaleMessage is null");
        }

        log.info("Creating invoice for clientId={}, saleId={}", clientId, sale.getSaleId());

        // Null safety: build notes safely
        String notes = "Odoo POS Order #" + sale.getSaleId();
        if (sale.getPosReference() != null && !sale.getPosReference().isBlank()) {
            notes += " (" + sale.getPosReference() + ")";
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", String.valueOf(clientId));
        params.add("currency", "EUR");
        params.add("notes", notes);

        Map<String, Object> response = post("/invoice/create", params);

        // Null safety: check response and result
        if (response == null || response.get("result") == null) {
            throw new RuntimeException("FossBilling returned null result for invoice creation");
        }

        Integer invoiceId = (Integer) response.get("result");
        log.info("Created draft invoice: id={}", invoiceId);
        return invoiceId;
    }

    /**
     * Add a single InvoiceItem line to an existing invoice.
     */
    public void addInvoiceItem(Integer invoiceId, InvoiceItem item) {
        // Null safety: check inputs
        if (invoiceId == null) {
            log.warn("addInvoiceItem called with null invoiceId — skipping");
            return;
        }
        if (item == null) {
            log.warn("addInvoiceItem called with null item — skipping");
            return;
        }

        // Null safety: use fallback title if null
        String title = item.getTitle() != null ? item.getTitle() : "Unknown product";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("invoice_id", String.valueOf(invoiceId));
        params.add("title", title);
        params.add("price", String.valueOf(item.getPrice()));
        params.add("quantity", String.valueOf(item.getQuantity()));

        post("/invoice/item/create", params);
        log.debug("Added item '{}' to invoice {}", title, invoiceId);
    }

    /**
     * Convert all SaleItems and add them to the invoice.
     */
    public void addAllItems(Integer invoiceId, List<SaleItem> saleItems) {
        // Null safety: check inputs
        if (invoiceId == null) {
            log.warn("addAllItems called with null invoiceId — skipping");
            return;
        }
        if (saleItems == null || saleItems.isEmpty()) {
            log.warn("addAllItems called with null or empty items list for invoiceId={} — skipping",
                    invoiceId);
            return;
        }

        for (SaleItem saleItem : saleItems) {
            // Null safety: skip null items in list
            if (saleItem == null) {
                log.warn("Null SaleItem in list for invoiceId={} — skipping", invoiceId);
                continue;
            }

            InvoiceItem item = new InvoiceItem();
            item.setTitle(saleItem.getProduct() != null
                    ? saleItem.getProduct() : "Unknown product");
            item.setQuantity(saleItem.getQuantity());
            item.setPrice(saleItem.getPriceUnit());
            addInvoiceItem(invoiceId, item);
        }
    }

    /**
     * Approve a draft invoice — changes status from draft to approved.
     */
    public void approveInvoice(Integer invoiceId) {
        // Null safety: check input
        if (invoiceId == null) {
            throw new RuntimeException("Cannot approve invoice: invoiceId is null");
        }

        log.info("Approving invoice: id={}", invoiceId);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        post("/invoice/approve", params);
        log.info("Invoice approved: id={}", invoiceId);
    }

    /**
     * Fetch full invoice details including the generated invoice number.
     * Returns an empty map if the response is null.
     */
    public Map<String, Object> getInvoice(Integer invoiceId) {
        // Null safety: check input
        if (invoiceId == null) {
            log.warn("getInvoice called with null invoiceId — returning empty map");
            return Collections.emptyMap();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        Map<String, Object> response = post("/invoice/get", params);

        // Null safety: check response
        if (response == null || response.get("result") == null) {
            log.warn("FossBilling returned null result for getInvoice({})", invoiceId);
            return Collections.emptyMap();
        }

        return (Map<String, Object>) response.get("result");
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    /**
     * Build the Basic Auth header: "Basic base64(admin:API_KEY)"
     */
    private String buildBasicAuthHeader() {
        // Null safety: check apiKey
        String key = apiKey != null ? apiKey : "";
        String credentials = "admin:" + key;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Execute a form-encoded POST request to the FossBilling API using Basic Auth.
     */
    private Map<String, Object> post(String endpoint, MultiValueMap<String, String> params) {
        // Null safety: check baseUrl
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new RuntimeException("FossBilling base URL is not configured");
        }

        String url = baseUrl + "/api/admin" + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", buildBasicAuthHeader());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        // Null safety: check HTTP status
        if (response == null) {
            throw new RuntimeException("No response received from FossBilling at: " + endpoint);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("FossBilling API error at " + endpoint
                    + ": HTTP " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();

        // Null safety: check error field in response body
        if (body != null && body.get("error") != null) {
            throw new RuntimeException("FossBilling API returned error: " + body.get("error"));
        }

        return body;
    }
}