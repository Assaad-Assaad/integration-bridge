package com.integration.odoo;

import com.google.gson.*;
import com.integration.config.RabbitMQConfig;
import com.integration.shared.ProcessedSalesTracker;
import com.integration.shared.XmlUtils; // Zorg dat dit import statement erbij staat
import com.integration.shared.model.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class OdooSender {

    @Autowired
    private OdooApiClient odooClient;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ProcessedSalesTracker tracker;

    // We gebruiken Gson nog steeds om de Odoo-data te lezen
    private final Gson gson = new Gson();

    @Scheduled(fixedDelay = 10000)
    public void runIntegrationTask() {
        try {
            System.out.println("🔍 Polling Odoo for new sales...");
            int uid = odooClient.authenticate();

            String ordersRaw = odooClient.getPaidOrders(uid);
            JsonObject responseWrapper = JsonParser.parseString(ordersRaw).getAsJsonObject();
            
            if (!responseWrapper.has("result")) return;
            JsonArray ordersArray = responseWrapper.getAsJsonArray("result");

            for (JsonElement orderElem : ordersArray) {
                JsonObject order = orderElem.getAsJsonObject();
                int saleId = order.get("id").getAsInt();

                if (tracker.isProcessed(saleId)) continue;

                System.out.println("✨ Processing New Sale: " + saleId);

                // We bouwen het Java object op
                SaleMessage msg = new SaleMessage();
                msg.sale_id = saleId;
                msg.pos_reference = order.get("pos_reference").getAsString();
                msg.amount_total = order.get("amount_total").getAsDouble();
                msg.amount_tax = order.get("amount_tax").getAsDouble();

                // --- SAFETY CHECK FOR PARTNER (CUSTOMER) ---
                JsonElement partnerElem = order.get("partner_id");
                if (partnerElem.isJsonArray()) {
                    int partnerId = partnerElem.getAsJsonArray().get(0).getAsInt();
                    String customerRaw = odooClient.getCustomerEmail(uid, partnerId);
                    
                    JsonArray customerResultArray = JsonParser.parseString(customerRaw).getAsJsonObject().getAsJsonArray("result");
                    
                    if (customerResultArray.size() > 0) {
                        JsonObject customerObj = customerResultArray.get(0).getAsJsonObject();
                        CustomerInfo customer = new CustomerInfo();
                        customer.odoo_partner_id = partnerId;
                        customer.name = customerObj.get("name").getAsString();
                        customer.email = (customerObj.has("email") && !customerObj.get("email").isJsonNull()) ? customerObj.get("email").getAsString() : "guest@example.com";
                        customer.phone = (customerObj.has("phone") && !customerObj.get("phone").isJsonNull()) ? customerObj.get("phone").getAsString() : "";
                        msg.customer = customer;
                    }
                } else {
                    CustomerInfo guest = new CustomerInfo();
                    guest.name = "Guest Customer";
                    guest.email = "guest@example.com";
                    msg.customer = guest;
                }

                // --- SAFETY CHECK FOR LINES ---
                msg.items = new ArrayList<>();
                JsonElement linesElem = order.get("lines");
                if (linesElem.isJsonArray()) {
                    JsonArray lineIds = linesElem.getAsJsonArray();
                    for (JsonElement lineId : lineIds) {
                        String lineRaw = odooClient.getOrderLines(uid, lineId.getAsInt());
                        JsonArray lineResultArray = JsonParser.parseString(lineRaw).getAsJsonObject().getAsJsonArray("result");
                        
                        if (lineResultArray.size() > 0) {
                            JsonObject lineObj = lineResultArray.get(0).getAsJsonObject();
                            SaleItem item = new SaleItem();
                            item.product = lineObj.get("product_id").getAsJsonArray().get(1).getAsString();
                            item.quantity = lineObj.get("qty").getAsDouble();
                            item.price_unit = lineObj.get("price_unit").getAsDouble();
                            item.price_incl = lineObj.get("price_subtotal_incl").getAsDouble();
                            msg.items.add(item);
                        }
                    }
                }

                // --- XML CONVERSION (DOCENT EIS) ---
                // We zetten het SaleMessage object om naar XML string via XmlUtils
                String xmlMessage = XmlUtils.toXml(msg);

                // We sturen de XML string naar de queue in plaats van JSON
                rabbitTemplate.convertAndSend(RabbitMQConfig.NEW_SALES_QUEUE, xmlMessage);

                tracker.markAsProcessed(saleId);
                System.out.println("🚀 XML Published to RabbitMQ: " + msg.pos_reference);
            }

        } catch (Exception e) {
            System.err.println("❌ Error in OdooSender: " + e.getMessage());
            e.printStackTrace();
        }
    }
}