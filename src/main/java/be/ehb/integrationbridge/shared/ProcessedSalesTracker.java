package be.ehb.integrationbridge.shared;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;


@Component
@Slf4j
public class ProcessedSalesTracker {

    private static final String COLLECTION = "processed_sales";

    private final Firestore firestore;
    private final Set<Integer> cache = ConcurrentHashMap.newKeySet();

    public ProcessedSalesTracker(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @PostConstruct
    public void loadFromFirestore() {
        if (firestore == null) {
            log.warn("Firestore unavailable — starting with empty in-memory cache");
            return;
        }
        try {
            firestore.collection(COLLECTION).get().get().getDocuments().forEach(doc -> {
                try {
                    cache.add(Integer.parseInt(doc.getId()));
                } catch (NumberFormatException e) {
                    log.warn("Skipping non-numeric document id in {}: {}", COLLECTION, doc.getId());
                }
            });
            log.info("Loaded {} processed sales from Firestore", cache.size());
        } catch (Exception e) {
            log.warn("Could not load processed sales from Firestore — starting with empty cache: {}",
                    e.getMessage());
        }
    }

    public boolean isProcessed(int saleId) {
        if (cache.contains(saleId)) {
            return true;
        }
        if (firestore == null) {
            return false;
        }
        try {
            DocumentSnapshot doc = firestore.collection(COLLECTION)
                    .document(String.valueOf(saleId))
                    .get()
                    .get();
            if (doc.exists()) {
                cache.add(saleId);
                return true;
            }
            return false;
        } catch (InterruptedException | ExecutionException | RuntimeException e) {
            log.warn("Firestore check failed for sale {} — using in-memory cache only: {}",
                    saleId, e.getMessage());
            return cache.contains(saleId);
        }
    }

    public void markAsProcessed(int saleId) {
        cache.add(saleId);
        if (firestore == null) {
            return;
        }
        try {
            DocumentReference ref = firestore.collection(COLLECTION).document(String.valueOf(saleId));
            ref.set(Map.of(
                    "saleId", saleId,
                    "processedAt", Instant.now().toString()
            )).get();
            log.debug("Marked sale {} as processed in Firestore", saleId);
        } catch (InterruptedException | ExecutionException | RuntimeException e) {
            log.warn("Could not persist sale {} to Firestore — kept in memory only: {}",
                    saleId, e.getMessage());
        }
    }
}