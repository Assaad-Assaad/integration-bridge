package com.integration.shared.model;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "sale") // Zorgt ervoor dat JAXB dit als start-tag ziet
public class SaleMessage {
    public String event_type = "new_sale";
    public String source = "odoo_pos";
    public int sale_id;
    public String pos_reference;
    public CustomerInfo customer;
    public List<SaleItem> items;
    public double amount_total;
    public double amount_tax;
}