package be.ehb.integrationbridge.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;


@Configuration
@Slf4j
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;

    @Value("${firebase.credentials.path:classpath:firebase-key.json}")
    private String credentialsPath;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase already initialized — skipping");
            return;
        }

        String resolvedPath = credentialsPath;
        if (credentialsPath.startsWith("/") && !credentialsPath.contains(":")) {
            resolvedPath = "file:" + credentialsPath;
        }
        try {
            Resource resource = resourceLoader.getResource(credentialsPath);
            if (!resource.exists()) {
                log.warn("Firebase credentials not found at '{}' — running without Firebase. " +
                                "ProcessedSalesTracker will use in-memory mode only.",
                        credentialsPath);
                return;
            }

            try (InputStream credentialsStream = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized from {}", credentialsPath);
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase — running without it: {}", e.getMessage());
        }
    }

    @Bean
    public Firestore firestore() {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp not initialized — Firestore bean will be null");
            return null;
        }
        return FirestoreClient.getFirestore();
    }
}
