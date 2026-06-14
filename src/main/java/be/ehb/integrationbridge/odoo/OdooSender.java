package be.ehb.integrationbridge.odoo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.ProcessedSalesTracker;
import be.ehb.integrationbridge.shared.XmlUtils;
import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OdooSender {

    private final OdooApiClient odooClient;
    private final RabbitTemplate rabbitTemplate;
    private final ProcessedSalesTracker tracker;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 10000)
    public void runIntegrationTask() {
        try {
            log.info("Polling Odoo for new sales...");

            String ordersRaw = odooClient.getPaidOrders();
            JsonNode root = objectMapper.readTree(ordersRaw);
            JsonNode orders = root.get("result");

            if (orders == null || !orders.isArray()) return;

            for (JsonNode order : orders) {
                int saleId = order.get("id").asInt();

                if (tracker.isProcessed(saleId)) continue;

                log.info("Processing New Sale: {}", saleId);

                SaleMessage msg = new SaleMessage();
                msg.setSaleId(saleId);
                msg.setPosReference(order.get("pos_reference").asText());
                msg.setPosReference(order.has("pos_reference") && !order.get("pos_reference").isNull()
                ? order.get("pos_reference").asText() : null);
                msg.setAmountTotal(order.get("amount_total").asDouble());
                msg.setAmountTax(order.get("amount_tax").asDouble());

                // Customer mapping
                JsonNode partnerNode = order.get("partner_id");
                if (partnerNode != null && partnerNode.isArray() && partnerNode.size() > 0) {
                    int pId = partnerNode.get(0).asInt();
                    String custRaw = odooClient.getCustomerEmail(pId);
                    JsonNode custNode = objectMapper.readTree(custRaw).get("result");
 
                    if (custNode != null && custNode.isArray() && custNode.size() > 0) {
                        JsonNode custRoot = custNode.get(0);
                        CustomerInfo ci = new CustomerInfo();
                        ci.setOdooPartnerId(pId);
                        ci.setName(custRoot.get("name").asText());
                        // Email mapping zonder fallback
                        ci.setEmail(custRoot.has("email") && !custRoot.get("email").isNull() ? custRoot.get("email").asText() : null);
                        msg.setCustomer(ci);
                    }
                }

                // Items mapping
                List<SaleItem> items = new ArrayList<>();
                JsonNode linesNode = order.get("lines");
                if (linesNode != null && linesNode.isArray()) {
                    for (JsonNode lineIdNode : linesNode) {
                        int lineId = lineIdNode.asInt(); // Forceer int
                        String lineRaw = odooClient.getOrderLines(lineId);
                        JsonNode lineNode = objectMapper.readTree(lineRaw).get("result");
 
                        if (lineNode != null && lineNode.isArray() && lineNode.size() > 0) {
                            JsonNode lineData = lineNode.get(0);
                            SaleItem si = new SaleItem();
                            si.setProduct(lineData.get("product_id").get(1).asText());
                            // Mocht quantity in het team-model een int zijn, gebruiken we asInt()
                            si.setQuantity(lineData.get("qty").asInt());
                            si.setPriceUnit(lineData.get("price_unit").asDouble());
                            si.setPriceIncl(lineData.get("price_subtotal_incl").asDouble());
                            items.add(si);
                        }
                    }
                }
                msg.setItems(items);

                // Publish XML via de team's XmlUtils
                String xml = XmlUtils.toXml(msg);
                rabbitTemplate.convertAndSend(RabbitMQConfig.NEW_SALES_QUEUE, xml);

                tracker.markAsProcessed(saleId);
                log.info("Published Sale {} to RabbitMQ", msg.getPosReference());
            }
        } catch (Exception e) {
            log.error("Error in OdooSender", e);
        }
    }
}