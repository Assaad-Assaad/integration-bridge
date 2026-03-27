package be.ehb.integrationbridge.shared.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement
public class InvoiceItem {
    private String title;
    private int quantity;
    private double price;
}
