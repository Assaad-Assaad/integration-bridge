package be.ehb.integrationbridge.shared.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement
public class HeartbeatMessage {
    private String source;
    private String status;
    private String timestamp;
}
