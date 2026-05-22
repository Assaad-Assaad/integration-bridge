package be.ehb.integrationbridge.shared.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "emailMessage")
public class EmailMessage {
    private String eventType;
    private String source;
    private int invoiceId;
    private String invoiceNumber;
    private String timestamp;
    private CustomerInfo customer;
    private double total;
    private String dueAt;

    @JacksonXmlElementWrapper(localName = "items")
    @JacksonXmlProperty(localName = "item")
    private List<InvoiceItem> items;

}
