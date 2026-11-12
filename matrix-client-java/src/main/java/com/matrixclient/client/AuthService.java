package com.matrixclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Manages authentication and session state for the Matrix client.
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final MatrixHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AuthService(MatrixHttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public CompletableFuture<String> login(String baseUrl, String userId, String password) {
        logger.info("Attempting login for user: {}", userId);
        
        // Note: In a real app, we'd use the baseUrl provided to discover the /login endpoint correctly.
        // For this implementation, we assume the baseUrl is the homeserver base.
        String loginPath = "/_matrix/client/v3/login";
        
        String jsonBody = String.format(
            "{\"user\":\"%s\", \"password\":\"%s\", \"device_name\":\"OpenClaw-JavaFX-Client\"}",
            userId, password
        );

        return httpClient.post(loginPath, jsonBody)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            String token = root.get("access_token").asText();
                            httpClient.setAccessToken(token);
                            logger.info("Login successful.");
                            return token;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse login response", e);
                        }
                    } else {
                        throw new RuntimeException("Login failed with status " + response.statusCode() + ": " + response.body());
                    }
                });
    }

    public CompletableFuture<Void> logout() {
        logger.info("Attempting logout...");
        return httpClient.post("/_matrix/client/v3/logout", "{}")
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        logger.warn("Logout failed: {}", response.body());
                    } else {
                        httpClient.setAccessToken(null);
                        logger.info("Logout successful.");
                    }
                });
    }
}
