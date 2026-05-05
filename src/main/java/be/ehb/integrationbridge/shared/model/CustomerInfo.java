package be.ehb.integrationbridge.shared.model;


import lombok.Data;

@Data
public class CustomerInfo {
    private int odooPartnerId;
    private String name;
    private String email;
    private String phone;
}
