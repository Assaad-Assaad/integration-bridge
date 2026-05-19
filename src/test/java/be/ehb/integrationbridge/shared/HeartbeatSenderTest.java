package be.ehb.integrationbridge.shared;

import static org.junit.jupiter.api.Assertions.*;
import be.ehb.integrationbridge.config.RabbitMQConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HeartbeatSenderTest {

    private RabbitTemplate rabbitTemplate;
    private HeartbeatSender sender;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        sender = new HeartbeatSender(rabbitTemplate);

        ReflectionTestUtils.setField(sender, "serviceName", "integration-bridge");
    }

    @Test
    void sendHeartbeatPublishesToHeartbeatQueue() {
        sender.sendHeartbeat();

        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.HEARTBEAT_QUEUE), anyString());
    }

    @Test
    void sendHeartbeatPayloadContainsSourceAndStatus() {
        ArgumentCaptor<String> xmlCaptor = ArgumentCaptor.forClass(String.class);

        sender.sendHeartbeat();

        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.HEARTBEAT_QUEUE), xmlCaptor.capture());
        String xml = xmlCaptor.getValue();

        assertTrue(xml.contains("<source>integration-bridge</source>"),
                "XML should contain the source");
        assertTrue(xml.contains("<status>alive</status>"),
                "XML should contain the alive status");
        assertTrue(xml.contains("<timestamp>"),
                "XML should contain a timestamp tag");
    }

    @Test
    void sendHeartbeatDoesNotThrowWhenRabbitFails() {
        doThrow(new AmqpException("Connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString());


        assertDoesNotThrow(() -> sender.sendHeartbeat());
    }

    @Test
    void sendHeartbeatCallsRabbitTemplateExactlyOnce() {
        sender.sendHeartbeat();

        verify(rabbitTemplate, times(1)).convertAndSend(anyString(), anyString());
    }

}