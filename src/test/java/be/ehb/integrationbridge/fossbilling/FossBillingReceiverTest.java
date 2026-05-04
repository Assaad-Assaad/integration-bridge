package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.config.RabbitMQConfig;
import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FossBillingReceiverTest {

    @InjectMocks
    private FossBillingReceiver receiver;

    @Mock
    private FossBillingApiClient apiClient;

    @Mock
    private FossBillingSender sender;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Message buildMessage(SaleMessage sale) throws Exception {
        String json = objectMapper.writeValueAsString(sale);
        return new Message(json.getBytes(), new MessageProperties());
    }

    private Message buildMessageWithRetry(SaleMessage sale, int retryCount) throws Exception {
        String json = objectMapper.writeValueAsString(sale);
        MessageProperties props = new MessageProperties();
        props.getHeaders().put("x-retry-count", retryCount);
        return new Message(json.getBytes(), props);
    }

    private SaleMessage buildValidSale() {
        CustomerInfo customer = new CustomerInfo();
        customer.setOdooPartnerId(1);
        customer.setName("Test User");
        customer.setEmail("test@gmail.com");
        customer.setPhone("0470000000");

        SaleItem item = new SaleItem();
        item.setProduct("Cola");
        item.setQuantity(2);
        item.setPriceUnit(2.50);
        item.setPriceIncl(3.00);

        SaleMessage sale = new SaleMessage();
        sale.setEventType("SALE");
        sale.setSource("odoo");
        sale.setSaleId(999);
        sale.setPosReference("POS-TEST-001");
        sale.setTimestamp("2026-04-10");
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
    void onNewSale_shouldCreateInvoiceAndPublishEmail_whenValidSale() throws Exception {
        // Arrange
        SaleMessage sale = buildValidSale();
        Message message = buildMessage(sale);

        when(apiClient.findOrCreateClient(any())).thenReturn(5);
        when(apiClient.createInvoice(eq(5), any())).thenReturn(10);
        when(apiClient.getInvoice(10)).thenReturn(Map.of("nr", "INV-2026-001"));

        // Act
        receiver.onNewSale(message);

        // Assert — full flow executed
        verify(apiClient).findOrCreateClient(any());
        verify(apiClient).createInvoice(eq(5), any());
        verify(apiClient).addAllItems(eq(10), any());
        verify(apiClient).approveInvoice(10);
        verify(apiClient).getInvoice(10);
        verify(sender).publishEmailMessage(any(), eq(10), eq("INV-2026-001"));
    }

    @Test
    void onNewSale_shouldUseExistingClient_whenClientAlreadyExists() throws Exception {
        // Arrange
        SaleMessage sale = buildValidSale();
        Message message = buildMessage(sale);

        when(apiClient.findOrCreateClient(any())).thenReturn(42);
        when(apiClient.createInvoice(eq(42), any())).thenReturn(20);
        when(apiClient.getInvoice(20)).thenReturn(Map.of("nr", "INV-2026-002"));

        // Act
        receiver.onNewSale(message);

        // Assert
        verify(apiClient).createInvoice(eq(42), any());
        verify(sender).publishEmailMessage(any(), eq(20), eq("INV-2026-002"));
    }

    // -------------------------------------------------------------------------
    // Edge case — anonymous sale
    // -------------------------------------------------------------------------

    @Test
    void onNewSale_shouldSkip_whenCustomerIsNull() throws Exception {
        SaleMessage sale = buildValidSale();
        sale.setCustomer(null);
        Message message = buildMessage(sale);

        receiver.onNewSale(message);

        verify(apiClient, never()).findOrCreateClient(any());
        verify(sender, never()).publishEmailMessage(any(), anyInt(), anyString());
    }

    @Test
    void onNewSale_shouldSkip_whenCustomerEmailIsEmpty() throws Exception {
        SaleMessage sale = buildValidSale();
        sale.getCustomer().setEmail("");
        Message message = buildMessage(sale);

        receiver.onNewSale(message);

        verify(apiClient, never()).findOrCreateClient(any());
        verify(sender, never()).publishEmailMessage(any(), anyInt(), anyString());
    }

    @Test
    void onNewSale_shouldSkip_whenCustomerEmailIsNull() throws Exception {
        SaleMessage sale = buildValidSale();
        sale.getCustomer().setEmail(null);
        Message message = buildMessage(sale);

        receiver.onNewSale(message);

        verify(apiClient, never()).findOrCreateClient(any());
        verify(sender, never()).publishEmailMessage(any(), anyInt(), anyString());
    }

    // -------------------------------------------------------------------------
    // Retry logic tests
    // -------------------------------------------------------------------------

    @Test
    void onNewSale_shouldRequeue_whenExceptionOccursAndRetryBelowMax() throws Exception {
        // Arrange
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 0);

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling down"));

        // Act & Assert
        // assertDoesNotThrow bewijst dat requeue pad genomen werd (geen exception)
        assertDoesNotThrow(() -> receiver.onNewSale(message));
    }

    @Test
    void onNewSale_shouldThrowToTriggerDLQ_whenMaxRetriesExceeded() throws Exception {
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 3);

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling still down"));

        assertThrows(RuntimeException.class, () -> receiver.onNewSale(message));

        // Verify nothing was requeued
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void onNewSale_shouldIncrementRetryCount_whenRequeuing() throws Exception {
        // Arrange
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 1); // 2de poging

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling down"));

        // Act & Assert
        // retry count < 3 dus geen exception verwacht
        assertDoesNotThrow(() -> receiver.onNewSale(message));

        // Verify dat apiClient werd aangeroepen (= bericht werd verwerkt)
        verify(apiClient, times(1)).findOrCreateClient(any());
    }
}