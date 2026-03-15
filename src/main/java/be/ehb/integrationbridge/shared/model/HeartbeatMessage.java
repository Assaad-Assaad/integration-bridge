package be.ehb.integrationbridge.shared.model;

import lombok.Data;

@Data
public class HeartbeatMessage {
    private String source;
    private String status;
    private String timestamp;
}
