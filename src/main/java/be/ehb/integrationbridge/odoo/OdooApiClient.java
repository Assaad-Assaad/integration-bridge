package be.ehb.integrationbridge.odoo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
    @Value("${odoo.user}") private String username;

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

    public int authenticate() {
        try {
            var request = createRequest("common", "authenticate", List.of(db, username, apiKey, Map.of()));
            var response = restTemplate.postForObject(url + "/jsonrpc", request, ObjectNode.class);
            return (response != null && response.has("result")) ? response.get("result").asInt() : -1;
        } catch (Exception e) {
            log.error("Odoo Auth failed", e);
            return -1;
        }
    }

    public String getPaidOrders() {
        var params = Map.of("fields", List.of("name", "partner_id", "amount_total", "amount_tax", "date_order", "state", "lines", "pos_reference"), "limit", 5);
        var args = List.of(db, userId, apiKey, "pos.order", "search_read", List.of(List.of("state", "=", "paid")), params);
        return restTemplate.postForObject(url + "/jsonrpc", createRequest("object", "execute_kw", args), String.class);
    }

    public String getCustomerEmail(int partnerId) {
        var params = Map.of("fields", List.of("name", "email", "phone"));
        var args = List.of(db, userId, apiKey, "res.partner", "read", List.of(partnerId), params);
        return restTemplate.postForObject(url + "/jsonrpc", createRequest("object", "execute_kw", args), String.class);
    }

    public String getOrderLines(int lineId) {
        var params = Map.of("fields", List.of("product_id", "qty", "price_unit", "price_subtotal_incl"));
        var args = List.of(db, userId, apiKey, "pos.order.line", "read", List.of(lineId), params);
        return restTemplate.postForObject(url + "/jsonrpc", createRequest("object", "execute_kw", args), String.class);
    }
}