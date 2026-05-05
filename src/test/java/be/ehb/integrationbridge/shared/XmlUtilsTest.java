package be.ehb.integrationbridge.shared;

import be.ehb.integrationbridge.exception.XmlSerializationException;
import be.ehb.integrationbridge.shared.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class XmlUtilsTest {

    @Test
    void saleMessageRoundTripPreservesAllFields() {
        SaleMessage original = buildSampleSale();

        String xml = XmlUtils.toXml(original);
        SaleMessage parsed = XmlUtils.fromXml(xml, SaleMessage.class);

        assertEquals(original, parsed);
    }

    @Test
    void emailMessageRoundTripPreservesAllFields() {
        EmailMessage original = buildSampleEmail();

        String xml = XmlUtils.toXml(original);
        EmailMessage parsed = XmlUtils.fromXml(xml, EmailMessage.class);

        assertEquals(original, parsed);
    }

    @Test
    void heartbeatMessageRoundTripPreservesAllFields() {
        HeartbeatMessage original = new HeartbeatMessage();
        original.setSource("odoo-sender");
        original.setStatus("alive");
        original.setTimestamp("2026-05-05T12:00:00Z");

        String xml = XmlUtils.toXml(original);
        HeartbeatMessage parsed = XmlUtils.fromXml(xml, HeartbeatMessage.class);

        assertEquals(original, parsed);
    }

    @Test
    void fromXmlRejectsXxePayload() {
        String evil = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <saleMessage><saleId>&xxe;</saleId></saleMessage>
            """;

        assertThrows(XmlSerializationException.class,
                () -> XmlUtils.fromXml(evil, SaleMessage.class));
    }

    @Test
    void fromXmlRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class,
                () -> XmlUtils.fromXml("", SaleMessage.class));
        assertThrows(IllegalArgumentException.class,
                () -> XmlUtils.fromXml("   ", SaleMessage.class));
    }

    @Test
    void fromXmlRejectsNullInput() {
        assertThrows(IllegalArgumentException.class,
                () -> XmlUtils.fromXml(null, SaleMessage.class));
    }

    @Test
    void toXmlRejectsNull() {
        assertThrows(NullPointerException.class,
                () -> XmlUtils.toXml(null));
    }


    private SaleMessage buildSampleSale() {
        CustomerInfo customer = new CustomerInfo();
        customer.setOdooPartnerId(42);
        customer.setName("Alice");
        customer.setEmail("alice@example.com");
        customer.setPhone("+32 123");

        SaleItem item = new SaleItem();
        item.setProduct("Beer");
        item.setQuantity(2);
        item.setPriceUnit(3.50);
        item.setPriceIncl(4.24);

        SaleMessage sale = new SaleMessage();
        sale.setEventType("sale.created");
        sale.setSource("odoo");
        sale.setSaleId(1001);
        sale.setPosReference("POS-001");
        sale.setTimestamp("2026-05-05T12:00:00Z");
        sale.setCustomer(customer);
        sale.setItems(List.of(item));
        sale.setAmountTotal(8.48);
        sale.setAmountTax(1.48);
        return sale;
    }

    private EmailMessage buildSampleEmail() {
        CustomerInfo customer = new CustomerInfo();
        customer.setOdooPartnerId(42);
        customer.setName("Alice");
        customer.setEmail("alice@example.com");
        customer.setPhone("+32 123");

        InvoiceItem item = new InvoiceItem();
        item.setTitle("Beer");
        item.setQuantity(2);
        item.setPrice(4.24);

        EmailMessage email = new EmailMessage();
        email.setEventType("invoice.created");
        email.setSource("fossbilling");
        email.setInvoiceId(2001);
        email.setInvoiceNumber("INV-001");
        email.setTimestamp("2026-05-05T12:05:00Z");
        email.setCustomer(customer);
        email.setItems(List.of(item));
        email.setTotal(8.48);
        email.setDueAt("2026-06-05");
        return email;
    }

}