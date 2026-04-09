package be.ehb.integrationbridge.shared;

import be.ehb.integrationbridge.shared.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlSerializationTest {

    // --- SaleMessage ---

    @Test
    void saleMessage_serializeAndDeserialize() throws Exception {
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

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<saleMessage"));
        assertTrue(xml.contains("<product>Beer</product>"));

        SaleMessage deserialized = XmlUtils.fromXml(xml, SaleMessage.class);
        assertEquals(original.getSaleId(), deserialized.getSaleId());
        assertEquals(original.getEventType(), deserialized.getEventType());
        assertEquals(original.getAmountTotal(), deserialized.getAmountTotal());
        assertEquals(original.getCustomer().getEmail(), deserialized.getCustomer().getEmail());
        assertEquals(1, deserialized.getItems().size());
        assertEquals("Beer", deserialized.getItems().get(0).getProduct());
    }

    // --- EmailMessage ---

    @Test
    void emailMessage_serializeAndDeserialize() throws Exception {
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

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<emailMessage"));
        assertTrue(xml.contains("<title>Beer</title>"));

        EmailMessage deserialized = XmlUtils.fromXml(xml, EmailMessage.class);
        assertEquals(original.getInvoiceId(), deserialized.getInvoiceId());
        assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
        assertEquals(original.getTotal(), deserialized.getTotal());
        assertEquals(original.getCustomer().getName(), deserialized.getCustomer().getName());
        assertEquals(1, deserialized.getItems().size());
        assertEquals("Beer", deserialized.getItems().get(0).getTitle());
    }

    // --- HeartbeatMessage ---

    @Test
    void heartbeatMessage_serializeAndDeserialize() throws Exception {
        HeartbeatMessage original = new HeartbeatMessage();
        original.setSource("odoo-sender");
        original.setStatus("alive");
        original.setTimestamp("2026-03-31T12:00:00");

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<heartbeatMessage"));
        assertTrue(xml.contains("<status>alive</status>"));

        HeartbeatMessage deserialized = XmlUtils.fromXml(xml, HeartbeatMessage.class);
        assertEquals(original.getSource(), deserialized.getSource());
        assertEquals(original.getStatus(), deserialized.getStatus());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
    }

    // --- CustomerInfo ---

    @Test
    void customerInfo_serializeAndDeserialize() throws Exception {
        CustomerInfo original = new CustomerInfo();
        original.setOdooPartnerId(7);
        original.setName("John Doe");
        original.setEmail("john@example.com");
        original.setPhone("+32123456789");

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<name>John Doe</name>"));

        CustomerInfo deserialized = XmlUtils.fromXml(xml, CustomerInfo.class);
        assertEquals(original.getOdooPartnerId(), deserialized.getOdooPartnerId());
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getEmail(), deserialized.getEmail());
        assertEquals(original.getPhone(), deserialized.getPhone());
    }

    // --- SaleItem ---

    @Test
    void saleItem_serializeAndDeserialize() throws Exception {
        SaleItem original = new SaleItem();
        original.setProduct("Cola");
        original.setQuantity(2);
        original.setPriceUnit(3.00);
        original.setPriceIncl(3.63);

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<product>Cola</product>"));

        SaleItem deserialized = XmlUtils.fromXml(xml, SaleItem.class);
        assertEquals(original.getProduct(), deserialized.getProduct());
        assertEquals(original.getQuantity(), deserialized.getQuantity());
        assertEquals(original.getPriceUnit(), deserialized.getPriceUnit());
        assertEquals(original.getPriceIncl(), deserialized.getPriceIncl());
    }

    // --- InvoiceItem ---

    @Test
    void invoiceItem_serializeAndDeserialize() throws Exception {
        InvoiceItem original = new InvoiceItem();
        original.setTitle("Cola");
        original.setQuantity(2);
        original.setPrice(3.63);

        String xml = XmlUtils.toXml(original);
        assertNotNull(xml);
        assertTrue(xml.contains("<title>Cola</title>"));

        InvoiceItem deserialized = XmlUtils.fromXml(xml, InvoiceItem.class);
        assertEquals(original.getTitle(), deserialized.getTitle());
        assertEquals(original.getQuantity(), deserialized.getQuantity());
        assertEquals(original.getPrice(), deserialized.getPrice());
    }
}
