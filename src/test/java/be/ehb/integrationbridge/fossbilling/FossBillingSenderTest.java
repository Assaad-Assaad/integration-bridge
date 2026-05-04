package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FossBillingSenderTest {

    @InjectMocks
    private FossBillingSender sender;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private SaleMessage buildValidSale() {
        CustomerInfo customer = new CustomerInfo();
        customer.setName("Test User");
        customer.setEmail("test@gmail.com");
        customer.setPhone("0470000000");

        SaleItem item = new SaleItem();
        item.setProduct("Cola");
        item.setQuantity(2);
        item.setPriceUnit(2.50);
        item.setPriceIncl(3.00);

        SaleMessage sale = new SaleMessage();
        sale.setSaleId(999);
        sale.setPosReference("POS-TEST-001");
        sale.setCustomer(customer);
        sale.setItems(List.of(item));
        sale.setAmountTotal(6.00);
        sale.setAmountTax(0.50);

        return sale;
    }

    // -------------------------------------------------------------------------
    // Happy path tests
    // -------------------------------------------------------------------------

    @Test
    void publishEmailMessage_shouldPublishToCorrectQueue() {
        // Arrange
        SaleMessage sale = buildValidSale();
        ArgumentCaptor<String> queueCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate, times(1))
                .convertAndSend(queueCaptor.capture(), anyString());
        assertEquals(RabbitMQConfig.SEND_EMAIL_QUEUE, queueCaptor.getValue());
    }

    @Test
    void publishEmailMessage_shouldPublishJsonWithInvoiceNumber() {
        // Arrange
        SaleMessage sale = buildValidSale();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        String json = messageCaptor.getValue();
        assertTrue(json.contains("INV-2026-001"));
        assertTrue(json.contains("test@gmail.com"));
    }

    @Test
    void publishEmailMessage_shouldSetCorrectEventType() {
        // Arrange
        SaleMessage sale = buildValidSale();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("INVOICE_CREATED"));
    }

    @Test
    void publishEmailMessage_shouldSetCorrectSource() {
        // Arrange
        SaleMessage sale = buildValidSale();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("fossbilling"));
    }

    @Test
    void publishEmailMessage_shouldIncludeAllSaleItems() {
        // Arrange
        SaleMessage sale = buildValidSale();

        SaleItem item2 = new SaleItem();
        item2.setProduct("Water");
        item2.setQuantity(1);
        item2.setPriceUnit(1.50);
        sale.setItems(List.of(sale.getItems().get(0), item2));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        String json = messageCaptor.getValue();
        assertTrue(json.contains("Cola"));
        assertTrue(json.contains("Water"));
    }

    @Test
    void publishEmailMessage_shouldIncludeCorrectTotal() {
        // Arrange
        SaleMessage sale = buildValidSale();
        sale.setAmountTotal(12.50);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("12.5"));
    }

    @Test
    void publishEmailMessage_shouldIncludeInvoiceId() {
        // Arrange
        SaleMessage sale = buildValidSale();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        sender.publishEmailMessage(sale, 42, "INV-2026-001");

        // Assert
        verify(rabbitTemplate).convertAndSend(anyString(), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("42"));
    }

    // -------------------------------------------------------------------------
    // Edge case — invalid email
    // -------------------------------------------------------------------------

    @Test
    void publishEmailMessage_shouldSkip_whenEmailIsNull() {
        // Arrange
        SaleMessage sale = buildValidSale();
        sale.getCustomer().setEmail(null);

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void publishEmailMessage_shouldSkip_whenEmailHasNoAtSign() {
        // Arrange
        SaleMessage sale = buildValidSale();
        sale.getCustomer().setEmail("invalidemail");

        // Act
        sender.publishEmailMessage(sale, 10, "INV-2026-001");

        // Assert
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    void publishEmailMessage_shouldThrow_whenRabbitTemplateFails() {
        // Arrange
        SaleMessage sale = buildValidSale();

        doThrow(new RuntimeException("RabbitMQ connection lost"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> sender.publishEmailMessage(sale, 10, "INV-2026-001"));
    }
}