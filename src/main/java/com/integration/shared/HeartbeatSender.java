package com.integration.shared;

import com.google.gson.Gson;
import com.integration.shared.model.HeartbeatMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    private final Gson gson = new Gson();

    @Scheduled(fixedRate = 1000) // Every 1 second
    public void sendHeartbeat() {
        HeartbeatMessage msg = new HeartbeatMessage();
        rabbitTemplate.convertAndSend("heartbeat_queue", gson.toJson(msg));
    }
}