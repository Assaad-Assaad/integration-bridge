package be.ehb.integrationbridge.shared;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
    private final Set<Integer> cache = ConcurrentHashMap.newKeySet();

    /**
     * Warm the in-memory cache from Firestore on startup.
     * If Firestore is unreachable, we start with an empty cache and rely on
     * fallback mode until Firestore becomes available again.
     */
    @PostConstruct
    public void loadFromFirestore() {
        try {
            Firestore db = FirestoreClient.getFirestore();
            db.collection(COLLECTION).get().get().getDocuments().forEach(doc -> {
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
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentSnapshot doc = db.collection(COLLECTION)
                    .document(String.valueOf(saleId))
                    .get()
                    .get();
            if (doc.exists()) {
                cache.add(saleId);  // populate cache for next time
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
        try {
            Firestore db = FirestoreClient.getFirestore();
            DocumentReference ref = db.collection(COLLECTION).document(String.valueOf(saleId));
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
