package be.ehb.integrationbridge.shared.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class EmailMessage {
    private String eventType;
    private String source;
    private int invoiceId;
    private String invoiceNumber;
    private String timestamp;
    private CustomerInfo customer;

    @XmlElementWrapper(name = "items")
    @XmlElement(name = "item")
    private List<InvoiceItem> items;
    private double total;
    private String dueAt;
}
