package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.shared.model.CustomerInfo;
import be.ehb.integrationbridge.shared.model.InvoiceItem;
import be.ehb.integrationbridge.shared.model.SaleItem;
import be.ehb.integrationbridge.shared.model.SaleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FossBillingApiClientTest {

    @InjectMocks
    private FossBillingApiClient apiClient;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually since we're not using Spring context
        ReflectionTestUtils.setField(apiClient, "baseUrl", "http://localhost:30003");
        ReflectionTestUtils.setField(apiClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(apiClient, "restTemplate", restTemplate);
    }

    // -------------------------------------------------------------------------
    // findClientByEmail tests
    // -------------------------------------------------------------------------

    @Test
    void findClientByEmail_shouldReturnClientId_whenClientExists() {
        // Arrange
        Map<String, Object> client = new HashMap<>();
        client.put("id", 42);

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(client);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", list);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        Integer clientId = apiClient.findClientByEmail("test@gmail.com");

        // Assert
        assertEquals(42, clientId);
    }

    @Test
    void findClientByEmail_shouldReturnNull_whenClientDoesNotExist() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", new ArrayList<>());
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        Integer clientId = apiClient.findClientByEmail("notfound@gmail.com");

        // Assert
        assertNull(clientId);
    }

    @Test
    void findClientByEmail_shouldReturnNull_whenExceptionOccurs() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act
        Integer clientId = apiClient.findClientByEmail("test@gmail.com");

        // Assert — should return null gracefully, not throw
        assertNull(clientId);
    }

    // -------------------------------------------------------------------------
    // createClient tests
    // -------------------------------------------------------------------------

    @Test
    void createClient_shouldReturnNewClientId() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 99);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("John Doe");
        customer.setEmail("john@gmail.com");
        customer.setPhone("0470000000");

        // Act
        Integer clientId = apiClient.createClient(customer);

        // Assert
        assertEquals(99, clientId);
    }

    @Test
    void createClient_shouldSplitFullNameCorrectly() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 1);
        responseBody.put("error", null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Jane Smith");
        customer.setEmail("jane@gmail.com");

        // Act
        apiClient.createClient(customer);

        // Assert — check that first_name and last_name are split correctly
        MultiValueMap<String, String> body =
                (MultiValueMap<String, String>) captor.getValue().getBody();
        assertEquals("Jane", body.getFirst("first_name"));
        assertEquals("Smith", body.getFirst("last_name"));
    }

    @Test
    void createClient_shouldHandleSingleNameCorrectly() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 1);
        responseBody.put("error", null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Madonna");
        customer.setEmail("madonna@gmail.com");

        // Act
        apiClient.createClient(customer);

        // Assert — last name defaults to "." when only one name given
        MultiValueMap<String, String> body =
                (MultiValueMap<String, String>) captor.getValue().getBody();
        assertEquals("Madonna", body.getFirst("first_name"));
        assertEquals(".", body.getFirst("last_name"));
    }

    // -------------------------------------------------------------------------
    // findOrCreateClient tests
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateClient_shouldReturnExistingClientId_whenFound() {
        // Arrange — mock findClientByEmail to return existing ID
        Map<String, Object> client = new HashMap<>();
        client.put("id", 5);
        List<Map<String, Object>> list = List.of(client);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", list);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setEmail("existing@gmail.com");
        customer.setName("Existing User");

        // Act
        Integer clientId = apiClient.findOrCreateClient(customer);

        // Assert — returns existing client, createClient never called
        assertEquals(5, clientId);
        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void findOrCreateClient_shouldCreateNewClient_whenNotFound() {
        // Arrange — first call (search) returns empty, second call (create) returns new ID
        Map<String, Object> searchResponse = new HashMap<>();
        searchResponse.put("list", new ArrayList<>());
        searchResponse.put("error", null);

        Map<String, Object> createResponse = new HashMap<>();
        createResponse.put("result", 77);
        createResponse.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(searchResponse, HttpStatus.OK))
                .thenReturn(new ResponseEntity<>(createResponse, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setEmail("new@gmail.com");
        customer.setName("New User");

        // Act
        Integer clientId = apiClient.findOrCreateClient(customer);

        // Assert
        assertEquals(77, clientId);
        verify(restTemplate, times(2))
                .postForEntity(anyString(), any(), eq(Map.class));
    }

    // -------------------------------------------------------------------------
    // createInvoice tests
    // -------------------------------------------------------------------------

    @Test
    void createInvoice_shouldReturnInvoiceId() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 15);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        SaleMessage sale = new SaleMessage();
        sale.setSaleId(999);
        sale.setPosReference("POS-TEST-001");

        // Act
        Integer invoiceId = apiClient.createInvoice(5, sale);

        // Assert
        assertEquals(15, invoiceId);
    }

    // -------------------------------------------------------------------------
    // addAllItems tests
    // -------------------------------------------------------------------------

    @Test
    void addAllItems_shouldCallAddInvoiceItemForEachSaleItem() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", true);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        SaleItem item1 = new SaleItem();
        item1.setProduct("Cola");
        item1.setQuantity(2);
        item1.setPriceUnit(2.50);

        SaleItem item2 = new SaleItem();
        item2.setProduct("Water");
        item2.setQuantity(1);
        item2.setPriceUnit(1.50);

        // Act
        apiClient.addAllItems(10, List.of(item1, item2));

        // Assert — 2 items = 2 API calls
        verify(restTemplate, times(2))
                .postForEntity(anyString(), any(), eq(Map.class));
    }

    // -------------------------------------------------------------------------
    // approveInvoice tests
    // -------------------------------------------------------------------------

    @Test
    void approveInvoice_shouldCallCorrectEndpoint() {
        // Arrange
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", true);
        responseBody.put("error", null);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        when(restTemplate.postForEntity(urlCaptor.capture(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        apiClient.approveInvoice(15);

        // Assert
        assertTrue(urlCaptor.getValue().contains("/invoice/approve"));
    }

    // -------------------------------------------------------------------------
    // Error handling tests
    // -------------------------------------------------------------------------

    @Test
    void post_shouldThrowException_whenFossBillingReturnsError() {
        // Arrange
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("result", null);
        Map<String, Object> error = new HashMap<>();
        error.put("message", "Email address is invalid");
        error.put("code", 9999);
        errorBody.put("error", error);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(errorBody, HttpStatus.OK));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Test User");
        customer.setEmail("invalid-email");

        // Act & Assert
        assertThrows(RuntimeException.class, () -> apiClient.createClient(customer));
    }

    @Test
    void post_shouldThrowException_whenHttpStatusIsNot2xx() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.internalServerError().build());

        // Act & Assert
        assertThrows(RuntimeException.class,
                () -> apiClient.approveInvoice(99));
    }
}