package com.integration.shared;

import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class ProcessedSalesTracker {
    // This stores IDs so we don't process the same order twice
    private final Set<Integer> processedIds = new HashSet<>();

    public boolean isProcessed(int saleId) {
        return processedIds.contains(saleId);
    }

    public void markAsProcessed(int saleId) {
        processedIds.add(saleId);
    }
}