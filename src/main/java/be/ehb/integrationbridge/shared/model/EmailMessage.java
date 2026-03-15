package be.ehb.integrationbridge.shared.model;

import lombok.Data;

import java.util.List;

@Data
public class EmailMessage {
    private String eventType;
    private String source;
    private int invoiceId;
    private String invoiceNumber;
    private String timestamp;
    private CustomerInfo customer;
    private List<InvoiceItem> items;
    private double total;
    private String dueAt;
}
