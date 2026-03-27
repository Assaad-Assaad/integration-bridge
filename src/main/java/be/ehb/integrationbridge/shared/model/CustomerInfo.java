package be.ehb.integrationbridge.shared.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement
public class CustomerInfo {
    private int odooPartnerId;
    private String name;
    private String email;
    private String phone;
}
