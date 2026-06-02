package be.ehb.integrationbridge.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class ProcessedSalesTracker {

    // Tijdelijke opslag in het geheugen omdat de Firebase key nog ontbreekt
    private final Set<Integer> processedIds = new HashSet<>();

    public boolean checkIfProcessed(int saleId) {
        return processedIds.contains(saleId);
    }

    public void markAsProcessed(int saleId) {
        processedIds.add(saleId);
        log.info("Saved sale ID {} to tracker", saleId);
    }
}