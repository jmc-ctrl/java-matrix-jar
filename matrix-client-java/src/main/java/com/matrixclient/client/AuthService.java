package com.matrixclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    }

    public CompletableFuture<String> login(String baseUrl, String userId, String password) {
        // Serialize credentials as JSON instead of interpolating untrusted input into JSON text.
        ObjectNode loginRequest = objectMapper.createObjectNode();
        loginRequest.put("user", userId);
        loginRequest.put("password", password);
        loginRequest.put("device_name", "Matrix-JavaFX-Client");
        final String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(loginRequest);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new IllegalStateException("Failed to encode login request", e));
        }

        logger.info("Attempting Matrix login.");
        String loginPath = "/_matrix/client/v3/login";

        return httpClient.post(loginPath, jsonBody)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode root = objectMapper.readTree(response.body());
                            JsonNode tokenNode = root == null ? null : root.get("access_token");
                            if (tokenNode == null || !tokenNode.isTextual() || tokenNode.asText().isBlank()) {
                                throw new IllegalStateException("Login response did not contain a valid access token");
                            }
                            String token = tokenNode.asText();
                            httpClient.setAccessToken(token);
                            logger.info("Login successful.");
                            return token;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse login response", e);
                        }
                    }
                    // Response bodies may contain server-controlled or sensitive data; do not expose them in UI/errors.
                    logger.warn("Matrix login failed with HTTP status {}.", response.statusCode());
                    throw new RuntimeException("Login failed with status " + response.statusCode());
                });
    }

    public CompletableFuture<Void> logout() {
        logger.info("Attempting logout...");
        return httpClient.post("/_matrix/client/v3/logout", "{}")
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        logger.warn("Logout failed with HTTP status {}.", response.statusCode());
                    } else {
                        httpClient.setAccessToken(null);
                        logger.info("Logout successful.");
                    }
                });
    }
}
