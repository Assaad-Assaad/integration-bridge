package com.integration.odoo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class OdooApiClientTest {

    @Autowired
    private OdooApiClient odooClient;

    @Test
    void contextLoads() {
        // This test proves that the OdooApiClient can be created by Spring
        assertNotNull(odooClient);
    }
}