package be.ehb.integrationbridge.shared;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.HeartbeatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;


@Component
@RequiredArgsConstructor
@Slf4j
public class HeartbeatSender {

    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.application.name:integration-bridge}")
    private String serviceName;

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        try {
            HeartbeatMessage hb = new HeartbeatMessage();
            hb.setSource(serviceName);
            hb.setStatus("alive");
            hb.setTimestamp(Instant.now().toString());

            String xml = XmlUtils.toXml(hb);
            rabbitTemplate.convertAndSend(RabbitMQConfig.HEARTBEAT_QUEUE, xml);
            log.debug("Heartbeat sent for {}", serviceName);
        } catch (Exception e) {
            log.warn("Failed to send heartbeat: {}", e.getMessage());
        }
    }
}
