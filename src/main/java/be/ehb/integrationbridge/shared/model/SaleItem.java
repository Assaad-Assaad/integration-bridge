package be.ehb.integrationbridge.shared.model;

import lombok.Data;

@Data
public class SaleItem {
    private String product;
    private int quantity;
    private double priceUnit;
    private double priceIncl;
}
