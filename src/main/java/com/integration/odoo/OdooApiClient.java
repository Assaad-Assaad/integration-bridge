package com.integration.odoo;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
//import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class OdooApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String url = "http://localhost:30002/jsonrpc";
    private final String db = "integration-group1";
    private final String user = "admin@group1-integration.local";
    private final String key = "9ffbfd7b980bc05ccb1431ae4a866f82d39e4e0d";

    private HttpEntity<String> createRequest(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    public int authenticate() {
        String body = "{\"jsonrpc\": \"2.0\", \"method\": \"call\", \"params\": {\"service\": \"common\", \"method\": \"authenticate\", \"args\": [\"" + db + "\", \"" + user + "\", \"" + key + "\", {}]}}";
        String resp = restTemplate.postForObject(url, createRequest(body), String.class);
        return JsonParser.parseString(resp).getAsJsonObject().get("result").getAsInt();
    }

    public String getPaidOrders(int uid) {
        String body = "{\"jsonrpc\": \"2.0\", \"method\": \"call\", \"params\": {\"service\": \"object\", \"method\": \"execute_kw\", \"args\": [\"" + db + "\", " + uid + ", \"" + key + "\", \"pos.order\", \"search_read\", [[[\"state\", \"=\", \"paid\"]]], {\"fields\": [\"name\", \"partner_id\", \"amount_total\", \"amount_tax\", \"date_order\", \"state\", \"lines\", \"pos_reference\"], \"limit\": 5}]}}";
        return restTemplate.postForObject(url, createRequest(body), String.class);
    }

    public String getCustomerEmail(int uid, int partnerId) {
        String body = "{\"jsonrpc\": \"2.0\", \"method\": \"call\", \"params\": {\"service\": \"object\", \"method\": \"execute_kw\", \"args\": [\"" + db + "\", " + uid + ", \"" + key + "\", \"res.partner\", \"read\", [[" + partnerId + "]], {\"fields\": [\"name\", \"email\", \"phone\", \"company_name\"]}]}}";
        return restTemplate.postForObject(url, createRequest(body), String.class);
    }

    public String getOrderLines(int uid, int lineId) {
        String body = "{\"jsonrpc\": \"2.0\", \"method\": \"call\", \"params\": {\"service\": \"object\", \"method\": \"execute_kw\", \"args\": [\"" + db + "\", " + uid + ", \"" + key + "\", \"pos.order.line\", \"read\", [[" + lineId + "]], {\"fields\": [\"product_id\", \"qty\", \"price_unit\", \"price_subtotal\", \"price_subtotal_incl\"]}]}}";
        return restTemplate.postForObject(url, createRequest(body), String.class);
    }
}