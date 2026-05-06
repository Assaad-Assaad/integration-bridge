package com.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // <--- THIS IS REQUIRED to make the OdooSender work!
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("---------------------------------------");
        System.out.println("🚀 ODOO BRIDGE SYSTEM IS NOW LIVE!");
        System.out.println("Checking Odoo every 10 seconds...");
        System.out.println("---------------------------------------");
    }
}