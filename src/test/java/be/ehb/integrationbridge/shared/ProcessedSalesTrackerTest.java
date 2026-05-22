package be.ehb.integrationbridge.shared;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProcessedSalesTrackerTest {

    private Firestore firestore;
    private CollectionReference collection;
    private DocumentReference document;
    private ProcessedSalesTracker tracker;

    @BeforeEach
    void setUp() {
        firestore = mock(Firestore.class);
        collection = mock(CollectionReference.class);
        document = mock(DocumentReference.class);

        when(firestore.collection("processed_sales")).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(document);
        when(document.set(any(Map.class))).thenReturn(ApiFutures.immediateFuture(null));

        tracker = new ProcessedSalesTracker(firestore);
    }

    @Test
    void worksWithoutFirestoreUsingInMemoryCacheOnly() {
        ProcessedSalesTracker offline = new ProcessedSalesTracker(null);

        assertFalse(offline.isProcessed(123));
        offline.markAsProcessed(123);
        assertTrue(offline.isProcessed(123));
    }

    @Test
    void loadFromFirestoreSkipsWhenFirestoreIsNull() {
        ProcessedSalesTracker offline = new ProcessedSalesTracker(null);

        assertDoesNotThrow(offline::loadFromFirestore);
        assertFalse(offline.isProcessed(1));
    }

    @Test
    void isProcessedReturnsTrueWhenInCache() {
        tracker.markAsProcessed(42);
        clearInvocations(firestore, collection, document);

        assertTrue(tracker.isProcessed(42));
        verify(firestore, never()).collection(anyString());
    }

    @Test
    void isProcessedQueriesFirestoreOnCacheMiss() {
        DocumentSnapshot snapshot = mockSnapshotExists(true);
        when(document.get()).thenReturn(ApiFutures.immediateFuture(snapshot));

        assertTrue(tracker.isProcessed(999));
        verify(firestore).collection("processed_sales");
        verify(collection).document("999");
    }

    @Test
    void isProcessedReturnsFalseWhenNotInFirestore() {
        DocumentSnapshot snapshot = mockSnapshotExists(false);
        when(document.get()).thenReturn(ApiFutures.immediateFuture(snapshot));

        assertFalse(tracker.isProcessed(999));
    }

    @Test
    void isProcessedCachesFirestoreHitsForNextTime() {
        DocumentSnapshot snapshot = mockSnapshotExists(true);
        when(document.get()).thenReturn(ApiFutures.immediateFuture(snapshot));

        tracker.isProcessed(50);
        tracker.isProcessed(50);

        verify(collection, times(1)).document("50");
    }

    @Test
    void isProcessedFallsBackToCacheWhenFirestoreFails() {

        tracker.markAsProcessed(7);
        clearInvocations(firestore, collection, document);

        when(document.get()).thenThrow(new RuntimeException("Firestore down"));

        assertTrue(tracker.isProcessed(7));
    }

    @Test
    void isProcessedReturnsFalseWhenFirestoreFailsAndCacheIsEmpty() {
        when(document.get()).thenThrow(new RuntimeException("Firestore down"));

        assertFalse(tracker.isProcessed(999));
    }

    @Test
    void markAsProcessedAddsToCacheAndFirestore() {
        tracker.markAsProcessed(123);

        assertTrue(tracker.isProcessed(123));
        verify(document).set(any(Map.class));
    }

    @Test
    void markAsProcessedKeepsCacheEntryEvenIfFirestoreFails() {
        when(document.set(any(Map.class))).thenThrow(new RuntimeException("Firestore down"));

        assertDoesNotThrow(() -> tracker.markAsProcessed(456));
        assertTrue(tracker.isProcessed(456));
    }

    // Helper method
    private DocumentSnapshot mockSnapshotExists(boolean exists) {
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(snapshot.exists()).thenReturn(exists);
        return snapshot;
    }

}