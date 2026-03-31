package be.ehb.integrationbridge.shared;

import be.ehb.integrationbridge.shared.model.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GsonSerializationTest {

    private final Gson gson = new Gson();

    // --- SaleMessage ---

    @Test
    void saleMessage_serializeAndDeserialize() {
        SaleMessage original = new SaleMessage();
        original.setEventType("new_sale");
        original.setSource("odoo");
        original.setSaleId(42);
        original.setPosReference("POS/001");
        original.setTimestamp("2026-03-31T12:00:00");
        original.setAmountTotal(25.50);
        original.setAmountTax(5.36);

        CustomerInfo customer = new CustomerInfo();
        customer.setOdooPartnerId(7);
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setPhone("+32123456789");
        original.setCustomer(customer);

        SaleItem item = new SaleItem();
        item.setProduct("Beer");
        item.setQuantity(3);
        item.setPriceUnit(5.00);
        item.setPriceIncl(6.05);
        original.setItems(List.of(item));

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"saleId\":42"));
        assertTrue(json.contains("\"product\":\"Beer\""));

        SaleMessage deserialized = gson.fromJson(json, SaleMessage.class);
        assertEquals(original.getSaleId(), deserialized.getSaleId());
        assertEquals(original.getEventType(), deserialized.getEventType());
        assertEquals(original.getAmountTotal(), deserialized.getAmountTotal());
        assertEquals(original.getCustomer().getEmail(), deserialized.getCustomer().getEmail());
        assertEquals(1, deserialized.getItems().size());
        assertEquals("Beer", deserialized.getItems().get(0).getProduct());
    }

    // --- EmailMessage ---

    @Test
    void emailMessage_serializeAndDeserialize() {
        EmailMessage original = new EmailMessage();
        original.setEventType("invoice_created");
        original.setSource("fossbilling");
        original.setInvoiceId(101);
        original.setInvoiceNumber("INV-001");
        original.setTimestamp("2026-03-31T12:05:00");
        original.setTotal(25.50);
        original.setDueAt("2026-04-30");

        CustomerInfo customer = new CustomerInfo();
        customer.setOdooPartnerId(7);
        customer.setName("John Doe");
        customer.setEmail("john@example.com");
        customer.setPhone("+32123456789");
        original.setCustomer(customer);

        InvoiceItem item = new InvoiceItem();
        item.setTitle("Beer");
        item.setQuantity(3);
        item.setPrice(6.05);
        original.setItems(List.of(item));

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"invoiceNumber\":\"INV-001\""));

        EmailMessage deserialized = gson.fromJson(json, EmailMessage.class);
        assertEquals(original.getInvoiceId(), deserialized.getInvoiceId());
        assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(original.getTotal(), deserialized.getTotal());
        assertEquals(original.getCustomer().getName(), deserialized.getCustomer().getName());
        assertEquals(1, deserialized.getItems().size());
        assertEquals("Beer", deserialized.getItems().get(0).getTitle());
    }

    // --- HeartbeatMessage ---

    @Test
    void heartbeatMessage_serializeAndDeserialize() {
        HeartbeatMessage original = new HeartbeatMessage();
        original.setSource("odoo-sender");
        original.setStatus("alive");
        original.setTimestamp("2026-03-31T12:00:00");

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"status\":\"alive\""));

        HeartbeatMessage deserialized = gson.fromJson(json, HeartbeatMessage.class);
        assertEquals(original.getSource(), deserialized.getSource());
        assertEquals(original.getStatus(), deserialized.getStatus());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
    }

    // --- CustomerInfo ---

    @Test
    void customerInfo_serializeAndDeserialize() {
        CustomerInfo original = new CustomerInfo();
        original.setOdooPartnerId(7);
        original.setName("John Doe");
        original.setEmail("john@example.com");
        original.setPhone("+32123456789");

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"John Doe\""));

        CustomerInfo deserialized = gson.fromJson(json, CustomerInfo.class);
        assertEquals(original.getOdooPartnerId(), deserialized.getOdooPartnerId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getEmail(), deserialized.getEmail());
        assertEquals(original.getPhone(), deserialized.getPhone());
    }

    // --- SaleItem ---

    @Test
    void saleItem_serializeAndDeserialize() {
        SaleItem original = new SaleItem();
        original.setProduct("Cola");
        original.setQuantity(2);
        original.setPriceUnit(3.00);
        original.setPriceIncl(3.63);

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"product\":\"Cola\""));

        SaleItem deserialized = gson.fromJson(json, SaleItem.class);
        assertEquals(original.getProduct(), deserialized.getProduct());
        assertEquals(original.getQuantity(), deserialized.getQuantity());
        assertEquals(original.getPriceUnit(), deserialized.getPriceUnit());
        assertEquals(original.getPriceIncl(), deserialized.getPriceIncl());
    }

    // --- InvoiceItem ---

    @Test
    void invoiceItem_serializeAndDeserialize() {
        InvoiceItem original = new InvoiceItem();
        original.setTitle("Cola");
        original.setQuantity(2);
        original.setPrice(3.63);

        String json = gson.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"title\":\"Cola\""));

        InvoiceItem deserialized = gson.fromJson(json, InvoiceItem.class);
        assertEquals(original.getTitle(), deserialized.getTitle());
        assertEquals(original.getQuantity(), deserialized.getQuantity());
        assertEquals(original.getPrice(), deserialized.getPrice());
    }
}
