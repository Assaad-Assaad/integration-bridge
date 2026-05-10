package com.integration.shared.model;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class SaleItem {
    public String product;
    public double quantity;
    public double price_unit;
    public double price_incl;
}