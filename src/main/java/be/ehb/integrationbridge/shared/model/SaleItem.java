package be.ehb.integrationbridge.shared.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement
public class SaleItem {
    private String product;
    private int quantity;
    private double priceUnit;
    private double priceIncl;
}
