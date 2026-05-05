package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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

    private final XmlMapper xmlMapper = new XmlMapper();

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Message buildMessage(SaleMessage sale) throws Exception {
        String xml = xmlMapper.writeValueAsString(sale);
        return new Message(xml.getBytes(), new MessageProperties());
    }

    private Message buildMessageWithRetry(SaleMessage sale, int retryCount) throws Exception {
        String xml = xmlMapper.writeValueAsString(sale);
        MessageProperties props = new MessageProperties();
        props.getHeaders().put("x-retry-count", retryCount);
        return new Message(xml.getBytes(), props);
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
        SaleMessage sale = buildValidSale();
        Message message = buildMessage(sale);

        when(apiClient.findOrCreateClient(any())).thenReturn(5);
        when(apiClient.createInvoice(eq(5), any())).thenReturn(10);
        when(apiClient.getInvoice(10)).thenReturn(Map.of("nr", "INV-2026-001"));

        receiver.onNewSale(message);

        verify(apiClient).findOrCreateClient(any());
        verify(apiClient).createInvoice(eq(5), any());
        verify(apiClient).addAllItems(eq(10), any());
        verify(apiClient).approveInvoice(10);
        verify(apiClient).getInvoice(10);
        verify(sender).publishEmailMessage(any(), eq(10), eq("INV-2026-001"));
    }

    @Test
    void onNewSale_shouldUseExistingClient_whenClientAlreadyExists() throws Exception {
        SaleMessage sale = buildValidSale();
        Message message = buildMessage(sale);

        when(apiClient.findOrCreateClient(any())).thenReturn(42);
        when(apiClient.createInvoice(eq(42), any())).thenReturn(20);
        when(apiClient.getInvoice(20)).thenReturn(Map.of("nr", "INV-2026-002"));

        receiver.onNewSale(message);

        verify(apiClient).createInvoice(eq(42), any());
        verify(sender).publishEmailMessage(any(), eq(20), eq("INV-2026-002"));
    }

    // -------------------------------------------------------------------------
    // Null safety tests
    // -------------------------------------------------------------------------

    @Test
    void onNewSale_shouldSkip_whenMessageIsNull() {
        receiver.onNewSale(null);

        verifyNoInteractions(apiClient);
        verifyNoInteractions(sender);
    }

    @Test
    void onNewSale_shouldSkip_whenBodyIsBlank() {
        Message message = new Message("".getBytes(), new MessageProperties());

        receiver.onNewSale(message);

        verifyNoInteractions(apiClient);
        verifyNoInteractions(sender);
    }

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

    @Test
    void onNewSale_shouldSkip_whenItemsIsEmpty() throws Exception {
        SaleMessage sale = buildValidSale();
        sale.setItems(List.of());
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
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 0);

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling down"));

        assertDoesNotThrow(() -> receiver.onNewSale(message));
    }

    @Test
    void onNewSale_shouldThrowToTriggerDLQ_whenMaxRetriesExceeded() throws Exception {
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 3);

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling still down"));

        assertThrows(RuntimeException.class, () -> receiver.onNewSale(message));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void onNewSale_shouldIncrementRetryCount_whenRequeuing() throws Exception {
        SaleMessage sale = buildValidSale();
        Message message = buildMessageWithRetry(sale, 1);

        when(apiClient.findOrCreateClient(any()))
                .thenThrow(new RuntimeException("FossBilling down"));

        assertDoesNotThrow(() -> receiver.onNewSale(message));
        verify(apiClient, times(1)).findOrCreateClient(any());
    }
}