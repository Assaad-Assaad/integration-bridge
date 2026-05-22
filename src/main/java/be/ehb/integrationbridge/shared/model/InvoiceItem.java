package be.ehb.integrationbridge.shared.model;


import lombok.Data;

@Data
public class InvoiceItem {
    private String title;
    private int quantity;
    private double price;
}
