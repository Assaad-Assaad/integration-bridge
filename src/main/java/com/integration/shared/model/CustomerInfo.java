package com.integration.shared.model;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class CustomerInfo {
    public int odoo_partner_id;
    public String name;
    public String email;
    public String phone;
}