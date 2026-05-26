package be.ehb.integrationbridge.fossbilling;

import be.ehb.integrationbridge.exception.ApiException;
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
        ReflectionTestUtils.setField(apiClient, "baseUrl", "http://localhost:30003");
        ReflectionTestUtils.setField(apiClient, "apiKey", "test-api-key");
    }

    // -------------------------------------------------------------------------
    // findClientByEmail tests
    // -------------------------------------------------------------------------

    @Test
    void findClientByEmail_shouldReturnClientId_whenClientExists() {
        Map<String, Object> client = new HashMap<>();
        client.put("id", 42);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(client);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", list);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        Integer clientId = apiClient.findClientByEmail("test@gmail.com");

        assertEquals(42, clientId);
    }

    @Test
    void findClientByEmail_shouldReturnNull_whenClientDoesNotExist() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", new ArrayList<>());
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        Integer clientId = apiClient.findClientByEmail("notfound@gmail.com");

        assertNull(clientId);
    }

    @Test
    void findClientByEmail_shouldReturnNull_whenEmailIsNull() {
        Integer clientId = apiClient.findClientByEmail(null);

        assertNull(clientId);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void findClientByEmail_shouldReturnNull_whenExceptionOccurs() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        Integer clientId = apiClient.findClientByEmail("test@gmail.com");

        assertNull(clientId);
    }

    // -------------------------------------------------------------------------
    // createClient tests
    // -------------------------------------------------------------------------

    @Test
    void createClient_shouldReturnNewClientId() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 99);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("John Doe");
        customer.setEmail("john@gmail.com");
        customer.setPhone("0470000000");

        Integer clientId = apiClient.createClient(customer);

        assertEquals(99, clientId);
    }

    @Test
    void createClient_shouldSplitFullNameCorrectly() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 1);
        responseBody.put("error", null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Jane Smith");
        customer.setEmail("jane@gmail.com");

        apiClient.createClient(customer);

        MultiValueMap<String, String> body =
                (MultiValueMap<String, String>) captor.getValue().getBody();
        assertEquals("Jane", body.getFirst("first_name"));
        assertEquals("Smith", body.getFirst("last_name"));
    }

    @Test
    void createClient_shouldHandleSingleNameCorrectly() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 1);
        responseBody.put("error", null);

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.postForEntity(anyString(), captor.capture(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Madonna");
        customer.setEmail("madonna@gmail.com");

        apiClient.createClient(customer);

        MultiValueMap<String, String> body =
                (MultiValueMap<String, String>) captor.getValue().getBody();
        assertEquals("Madonna", body.getFirst("first_name"));
        assertEquals(".", body.getFirst("last_name"));
    }

    @Test
    void createClient_shouldThrowApiException_whenCustomerIsNull() {
        assertThrows(ApiException.class, () -> apiClient.createClient(null));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createClient_shouldThrowApiException_whenEmailIsNull() {
        CustomerInfo customer = new CustomerInfo();
        customer.setName("Test User");
        customer.setEmail(null);

        assertThrows(ApiException.class, () -> apiClient.createClient(customer));
        verifyNoInteractions(restTemplate);
    }

    // -------------------------------------------------------------------------
    // findOrCreateClient tests
    // -------------------------------------------------------------------------

    @Test
    void findOrCreateClient_shouldReturnExistingClientId_whenFound() {
        Map<String, Object> client = new HashMap<>();
        client.put("id", 5);
        List<Map<String, Object>> list = List.of(client);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("list", list);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        CustomerInfo customer = new CustomerInfo();
        customer.setEmail("existing@gmail.com");
        customer.setName("Existing User");

        Integer clientId = apiClient.findOrCreateClient(customer);

        assertEquals(5, clientId);
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void findOrCreateClient_shouldCreateNewClient_whenNotFound() {
        Map<String, Object> searchResponse = new HashMap<>();
        searchResponse.put("list", new ArrayList<>());
        searchResponse.put("error", null);

        Map<String, Object> createResponse = new HashMap<>();
        createResponse.put("result", 77);
        createResponse.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(searchResponse))
                .thenReturn(ResponseEntity.ok(createResponse));

        CustomerInfo customer = new CustomerInfo();
        customer.setEmail("new@gmail.com");
        customer.setName("New User");

        Integer clientId = apiClient.findOrCreateClient(customer);

        assertEquals(77, clientId);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void findOrCreateClient_shouldThrowApiException_whenCustomerIsNull() {
        assertThrows(ApiException.class, () -> apiClient.findOrCreateClient(null));
        verifyNoInteractions(restTemplate);
    }

    // -------------------------------------------------------------------------
    // createInvoice tests
    // -------------------------------------------------------------------------

    @Test
    void createInvoice_shouldReturnInvoiceId() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", 15);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        SaleMessage sale = new SaleMessage();
        sale.setSaleId(999);
        sale.setPosReference("POS-TEST-001");

        Integer invoiceId = apiClient.createInvoice(5, sale);

        assertEquals(15, invoiceId);
    }

    @Test
    void createInvoice_shouldThrowApiException_whenClientIdIsNull() {
        assertThrows(ApiException.class,
                () -> apiClient.createInvoice(null, new SaleMessage()));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void createInvoice_shouldThrowApiException_whenSaleIsNull() {
        assertThrows(ApiException.class, () -> apiClient.createInvoice(5, null));
        verifyNoInteractions(restTemplate);
    }

    // -------------------------------------------------------------------------
    // addAllItems tests
    // -------------------------------------------------------------------------

    @Test
    void addAllItems_shouldCallAddInvoiceItemForEachSaleItem() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", true);
        responseBody.put("error", null);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        SaleItem item1 = new SaleItem();
        item1.setProduct("Cola");
        item1.setQuantity(2);
        item1.setPriceUnit(2.50);

        SaleItem item2 = new SaleItem();
        item2.setProduct("Water");
        item2.setQuantity(1);
        item2.setPriceUnit(1.50);

        apiClient.addAllItems(10, List.of(item1, item2));

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Map.class));
    }

    // -------------------------------------------------------------------------
    // approveInvoice tests
    // -------------------------------------------------------------------------

    @Test
    void approveInvoice_shouldCallCorrectEndpoint() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("result", true);
        responseBody.put("error", null);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        when(restTemplate.postForEntity(urlCaptor.capture(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        apiClient.approveInvoice(15);

        assertTrue(urlCaptor.getValue().contains("/invoice/approve"));
    }

    @Test
    void approveInvoice_shouldThrowApiException_whenInvoiceIdIsNull() {
        assertThrows(ApiException.class, () -> apiClient.approveInvoice(null));
        verifyNoInteractions(restTemplate);
    }

    // -------------------------------------------------------------------------
    // Error handling tests
    // -------------------------------------------------------------------------

    @Test
    void post_shouldThrowApiException_whenFossBillingReturnsError() {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("message", "Email address is invalid");
        errorMap.put("code", 9999);

        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("result", null);
        errorBody.put("error", errorMap);

        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(errorBody));

        CustomerInfo customer = new CustomerInfo();
        customer.setName("Test User");
        customer.setEmail("test@gmail.com");

        assertThrows(ApiException.class, () -> apiClient.createClient(customer));
    }

    @Test
    void post_shouldThrowApiException_whenHttpStatusIsNot2xx() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.internalServerError().build());

        assertThrows(ApiException.class, () -> apiClient.approveInvoice(99));
    }
}