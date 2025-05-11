package sh.tbawor.javanalyser.util;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Utility class to handle batch processing of items with progress tracking.
 * Used to standardize batch operations across the application.
 */
@Slf4j
public class BatchProcessor {

    /**
     * Creates batches of items with a specified maximum size
     *
     * @param items The items to batch
     * @param batchSize The maximum size of each batch
     * @return A list of batches
     */
    public static <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            batches.add(new ArrayList<>(items.subList(i, end)));
        }
        return batches;
    }

    /**
     * Process items in batches with progress tracking
     *
     * @param <T> Type of items to process
     * @param items List of items to process
     * @param batchSize Size of each batch
     * @param maxItems Maximum number of items to process
     * @param progressLogInterval Interval for logging progress (in percentage)
     * @param operationName Name of the operation for logging
     * @param processor Function to process each item
     * @return Number of successfully processed items
     */
    public static <T> int processBatchesWithProgress(
            List<T> items,
            int batchSize,
            int maxItems,
            int progressLogInterval,
            String operationName,
            Consumer<T> processor) {
        
        if (items == null || items.isEmpty()) {
            log.info("No items to process for {}", operationName);
            return 0;
        }
        
        // Create batches
        List<List<T>> batches = createBatches(items, batchSize);
        int totalItems = items.size();
        
        log.info("Starting {} on {} items with batch size {} and max items {}",
                operationName, totalItems, batchSize, maxItems);
        
        AtomicInteger processedItems = new AtomicInteger(0);
        AtomicInteger successfulItems = new AtomicInteger(0);
        int lastLoggedPercentage = 0;
        
        // Process each batch
        for (List<T> batch : batches) {
            // Process each item in the batch
            for (T item : batch) {
                // Check if we've reached the maximum number of items
                if (successfulItems.get() >= maxItems) {
                    log.warn("Reached maximum item count ({}) for {}. Stopping processing.",
                            maxItems, operationName);
                    break;
                }
                
                try {
                    // Process the item
                    processor.accept(item);
                    successfulItems.incrementAndGet();
                } catch (Exception e) {
                    log.error("Error processing item during {}: {}", operationName, item, e);
                }
                
                // Update progress
                int currentProcessed = processedItems.incrementAndGet();
                int percentage = (currentProcessed * 100) / totalItems;
                
                // Log progress at intervals
                if (percentage >= lastLoggedPercentage + progressLogInterval) {
                    log.info("{} progress: {}% ({}/{} items, {} successful)",
                            operationName, percentage, currentProcessed, totalItems, successfulItems.get());
                    lastLoggedPercentage = percentage;
                }
            }
            
            // Check if we've reached the maximum number of items
            if (successfulItems.get() >= maxItems) {
                break;
            }
        }
        
        log.info("{} completed: processed {} items, {} successful",
                operationName, processedItems.get(), successfulItems.get());
        
        return successfulItems.get();
    }
}