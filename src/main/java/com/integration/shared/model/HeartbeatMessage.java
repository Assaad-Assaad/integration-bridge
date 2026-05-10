package com.integration.shared.model;

import java.time.Instant;

public class HeartbeatMessage {
    public String source = "odoo_sender";
    public String status = "alive";
    public String timestamp = Instant.now().toString();
}