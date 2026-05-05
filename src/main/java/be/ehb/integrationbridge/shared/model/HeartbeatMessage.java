package be.ehb.integrationbridge.shared.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "heartbeatMessage")
public class HeartbeatMessage {
    private String source;
    private String status;
    private String timestamp;
}
