package be.ehb.integrationbridge.shared.model;

import lombok.Data;

import java.util.List;

@Data
public class SaleMessage {
    private String eventType;
    private String source;
    private int saleId;
    private String posReference;
    private String timestamp;
    private CustomerInfo customer;
    private List<SaleItem> items;
    private double amountTotal;
    private double amountTax;
}
