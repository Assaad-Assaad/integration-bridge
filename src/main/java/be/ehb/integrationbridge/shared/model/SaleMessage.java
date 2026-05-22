package be.ehb.integrationbridge.shared.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "saleMessage")
public class SaleMessage {
    private String eventType;
    private String source;
    private int saleId;
    private String posReference;
    private String timestamp;
    private CustomerInfo customer;
    private double amountTotal;
    private double amountTax;

    @JacksonXmlElementWrapper(localName = "items")
    @JacksonXmlProperty(localName = "item")
    private List<SaleItem> items;
}
