package com.matrixclient.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core Matrix client service for handling HTTP communication with the homeserver.
 * Follows the Matrix Client-Server API specification.
 */
public class MatrixHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(MatrixHttpClient.class);
    private final HttpClient httpClient;
    private final String baseUrl;
    private String accessToken;

    public MatrixHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public CompletableFuture<HttpResponse<String>> get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeader())
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);
    }

    public CompletableFuture<HttpResponse<String>> post(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", getAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);
    }

    private String getAuthHeader() {
        return accessToken != null ? "Bearer " + accessToken : "";
    }

    private HttpResponse<String> handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 400) {
            logger.error("Matrix API error: status={}, body={}", status, response.body());
        } else {
            logger.debug("Matrix API success: status={}", status);
        }
        return response;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
