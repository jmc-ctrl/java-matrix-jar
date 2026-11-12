package com.matrixclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages the Matrix synchronization loop, handling incremental sync and events.
 */
public class SyncService {
    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private final MatrixHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String nextBatch;
    private boolean running = false;

    public SyncService(MatrixHttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void startSyncLoop(Consumer<JsonNode> onSyncReceived, Consumer<Throwable> onError) {
        if (running) return;
        running = true;
        logger.info("Starting sync loop.");
        performSync(onSyncReceived, onError);
    }

    public void stopSyncLoop() {
        running = false;
        logger.info("Stopping sync loop.");
    }

    private void performSync(Consumer<JsonNode> onSyncReceived, Consumer<Throwable> onError) {
        if (!running) return;

        String syncPath = "/_matrix/client/v3/sync" + (nextBatch != null ? "?since=" + nextBatch : "");

        httpClient.get(syncPath).thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    JsonNode root = objectMapper.readTree(response.body());
                    nextBatch = root.get("next_batch").asText();
                    onSyncReceived.accept(root);
                    
                    performSync(onSyncReceived, onError);
                } catch (Exception e) {
                    onError.accept(e);
                }
            } else if (response.statusCode() == 429) {
                String retryAfter = response.headers().firstValue("retry-after").orElse("5");
                long delay = Long.parseLong(retryAfter) * 1000L;
                logger.warn("Rate limited. Retrying after {}ms", delay);
                scheduleRetry(delay, onSyncReceived, onError);
            } else {
                onError.accept(new RuntimeException("Sync failed with status: " + response.statusCode()));
                running = false;
            }
        }).exceptionally(ex -> {
            onError.accept(ex);
            return null;
        });
    }

    private void scheduleRetry(long delayMs, Consumer<JsonNode> onSyncReceived, Consumer<Throwable> onError) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                performSync(onSyncReceived, onError);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
