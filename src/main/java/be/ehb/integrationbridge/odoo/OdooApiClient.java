package be.ehb.integrationbridge.odoo;

import be.ehb.integrationbridge.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OdooApiClient {

    private final RestTemplate restTemplate;

    @Value("${odoo.url}") private String url;
    @Value("${odoo.db}") private String db;
    @Value("${odoo.userId}") private int userId;
    @Value("${odoo.apiKey}") private String apiKey;

    public String getPaidOrders() {
        var params = Map.of(
                "fields", List.of("name", "partner_id", "amount_total", "amount_tax", "date_order", "state", "lines", "pos_reference"),
                "limit", 5
        );
        return callOdoo("pos.order", "search_read", List.of(List.of("state", "=", "paid")), params);
    }

    public String getCustomerEmail(int partnerId) {
        var params = Map.of("fields", List.of("name", "email", "phone"));
        return callOdoo("res.partner", "read", List.of(partnerId), params);
    }

    public String getOrderLines(int lineId) {
        var params = Map.of("fields", List.of("product_id", "qty", "price_unit", "price_subtotal_incl"));
        return callOdoo("pos.order.line", "read", List.of(lineId), params);
    }

    /**
     * Sends a JSON-RPC call to Odoo's "object" endpoint, which lets you call
     * any method on any Odoo model (e.g. search_read on pos.order).
     * Wraps network errors as ApiException with an "Odoo" prefix.
     */
    private String callOdoo(String model, String method, Object domain, Object params) {
        var args = List.of(db, userId, apiKey, model, method, domain, params);
        var request = createRequest("object", "execute_kw", args);
        try {
            return restTemplate.postForObject(url + "/jsonrpc", request, String.class);
        } catch (RestClientException e) {
            throw new ApiException("Odoo API error on " + model + "." + method + ": " + e.getMessage(), e);
        }
    }

    private HttpEntity<Map<String, Object>> createRequest(String service, String method, List<Object> args) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "jsonrpc", "2.0",
                "method", "call",
                "params", Map.of("service", service, "method", method, "args", args)
        );
        return new HttpEntity<>(body, headers);
    }
}