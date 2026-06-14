package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.exception.ApiException;
import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FossBillingApiClient {

    @Value("${fossbilling.url}")
    private String baseUrl;

    @Value("${fossbilling.apiKey}")
    private String apiKey;

    private final RestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // CLIENT METHODS
    // -------------------------------------------------------------------------

    public Integer findClientByEmail(String email) {
        if (email == null || email.isBlank()) {
            log.warn("findClientByEmail called with null or blank email — returning null");
            return null;
        }

        // Note: no broad try/catch here. Network errors must propagate as ApiException
        // so the receiver does NOT interpret them as "client doesn't exist" (would create duplicates).
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("email", email);

        Map<String, Object> response = post("/client/search", params);

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

        Map<String, Object> firstClient = list.get(0);
        if (firstClient == null || firstClient.get("id") == null) {
            log.warn("Client found but id is null for email: {}", email);
            return null;
        }

        Integer clientId = (Integer) firstClient.get("id");
        log.info("Found existing FossBilling client: id={}, email={}", clientId, email);
        return clientId;
    }

    public Integer createClient(CustomerInfo customer) {
        if (customer == null) {
            throw new ApiException("Cannot create client: CustomerInfo is null");
        }
        if (customer.getEmail() == null || customer.getEmail().isBlank()) {
            throw new ApiException("Cannot create client: email is null or blank");
        }

        log.info("Creating new FossBilling client for: {}", customer.getEmail());

        String fullName = customer.getName() != null ? customer.getName().trim() : "";
        String[] nameParts = fullName.split(" ", 2);
        String firstName = nameParts.length > 0 && !nameParts[0].isBlank()
                ? nameParts[0] : "Unknown";
        String lastName = nameParts.length > 1 && !nameParts[1].isBlank()
                ? nameParts[1] : ".";

        String generatedPassword = "Fb-" + UUID.randomUUID().toString().substring(0, 12) + "!";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("email", customer.getEmail());
        params.add("first_name", firstName);
        params.add("last_name", lastName);
        params.add("password", generatedPassword);
        params.add("country", "BE");
        params.add("currency", "EUR");

        if (customer.getPhone() != null && !customer.getPhone().isBlank()) {
            params.add("phone", customer.getPhone());
        }

        Map<String, Object> response = post("/client/create", params);

        if (response == null || response.get("result") == null) {
            throw new ApiException("FossBilling returned null result for client creation");
        }

        Integer clientId = (Integer) response.get("result");
        log.info("Created FossBilling client: id={}", clientId);
        return clientId;
    }

    public Integer findOrCreateClient(CustomerInfo customer) {
        if (customer == null) {
            throw new ApiException("Cannot find or create client: CustomerInfo is null");
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

    public Integer createInvoice(Integer clientId, SaleMessage sale) {
        if (clientId == null) {
            throw new ApiException("Cannot create invoice: clientId is null");
        }
        if (sale == null) {
            throw new ApiException("Cannot create invoice: SaleMessage is null");
        }

        log.info("Creating invoice for clientId={}, saleId={}", clientId, sale.getSaleId());

        String notes = "Odoo POS Order #" + sale.getSaleId();
        if (sale.getPosReference() != null && !sale.getPosReference().isBlank()) {
            notes += " (" + sale.getPosReference() + ")";
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", String.valueOf(clientId));
        params.add("currency", "EUR");
        params.add("notes", notes);

        Map<String, Object> response = post("/invoice/create", params);

        if (response == null || response.get("result") == null) {
            throw new ApiException("FossBilling returned null result for invoice creation");
        }

        Integer invoiceId = (Integer) response.get("result");
        log.info("Created draft invoice: id={}", invoiceId);
        return invoiceId;
    }

    public void addInvoiceItem(Integer invoiceId, InvoiceItem item) {
        if (invoiceId == null) {
            log.warn("addInvoiceItem called with null invoiceId — skipping");
            return;
        }
        if (item == null) {
            log.warn("addInvoiceItem called with null item — skipping");
            return;
        }

        String title = item.getTitle() != null ? item.getTitle() : "Unknown product";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("invoice_id", String.valueOf(invoiceId));
        params.add("title", title);
        params.add("price", String.valueOf(item.getPrice()));
        params.add("quantity", String.valueOf(item.getQuantity()));

        post("/invoice/item/create", params);
        log.debug("Added item '{}' to invoice {}", title, invoiceId);
    }

    public void addAllItems(Integer invoiceId, List<SaleItem> saleItems) {
        if (invoiceId == null) {
            log.warn("addAllItems called with null invoiceId — skipping");
            return;
        }
        if (saleItems == null || saleItems.isEmpty()) {
            log.warn("addAllItems called with empty items for invoiceId={} — skipping", invoiceId);
            return;
        }

        for (SaleItem saleItem : saleItems) {
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

    public void approveInvoice(Integer invoiceId) {
        if (invoiceId == null) {
            throw new ApiException("Cannot approve invoice: invoiceId is null");
        }

        log.info("Approving invoice: id={}", invoiceId);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        post("/invoice/approve", params);
        log.info("Invoice approved: id={}", invoiceId);
    }

    public Map<String, Object> getInvoice(Integer invoiceId) {
        if (invoiceId == null) {
            log.warn("getInvoice called with null invoiceId — returning empty map");
            return Collections.emptyMap();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("id", String.valueOf(invoiceId));

        Map<String, Object> response = post("/invoice/get", params);

        if (response == null || response.get("result") == null) {
            log.warn("FossBilling returned null result for getInvoice({})", invoiceId);
            return Collections.emptyMap();
        }

        return (Map<String, Object>) response.get("result");
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private String buildBasicAuthHeader() {
        String key = apiKey != null ? apiKey : "";
        String credentials = "admin:" + key;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private Map<String, Object> post(String endpoint, MultiValueMap<String, String> params) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ApiException("FossBilling base URL is not configured");
        }

        String url = baseUrl + "/api/admin" + endpoint;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8));
        headers.set("Authorization", buildBasicAuthHeader());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, request, Map.class);
        } catch (RestClientException e) {
            // Network error must propagate as ApiException so the receiver retries
            // instead of treating it as "client doesn't exist"
            throw new ApiException(
                    "Network error calling FossBilling at " + endpoint + ": " + e.getMessage(), e);
        }

        if (response == null) {
            throw new ApiException("No response received from FossBilling at: " + endpoint);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ApiException("FossBilling HTTP error at " + endpoint
                    + ": " + response.getStatusCode());
        }

        Map<String, Object> body = response.getBody();

        if (body != null && body.get("error") != null) {
            Object errorObj = body.get("error");
            if (errorObj instanceof Map) {
                Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                String message = String.valueOf(errorMap.get("message"));
                throw new ApiException("FossBilling error at " + endpoint + ": " + message);
            }
            throw new ApiException("FossBilling error at " + endpoint + ": " + errorObj);
        }

        return body;
    }
}