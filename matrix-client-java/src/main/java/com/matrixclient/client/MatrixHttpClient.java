package com.matrixclient.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core Matrix client service for handling HTTP communication with the homeserver.
 * Follows the Matrix Client-Server API specification.
 */
public class MatrixHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(MatrixHttpClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final URI baseUri;
    private volatile String accessToken;

    public MatrixHttpClient(String baseUrl) {
        this.baseUri = validateBaseUrl(baseUrl);
        String normalized = baseUri.toString();
        this.baseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // Never follow redirects with an Authorization header to another endpoint.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static URI validateBaseUrl(String value) {
        Objects.requireNonNull(value, "Homeserver URL must not be null");
        final URI uri;
        try {
            uri = URI.create(value.trim()).normalize();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid homeserver URL", e);
        }

        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Homeserver URL must be an absolute HTTP(S) URL without credentials, query, or fragment");
        }

        // Permit plaintext HTTP only for local development; credentials must use TLS on remote homeservers.
        if ("http".equalsIgnoreCase(scheme) && !isLoopback(uri.getHost())) {
            throw new IllegalArgumentException("HTTPS is required for non-local homeservers");
        }
        return uri;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equalsIgnoreCase(host);
    }

    private URI requestUri(String path) {
        Objects.requireNonNull(path, "Request path must not be null");
        if (!path.startsWith("/") || path.startsWith("//") || path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Request path must be a safe absolute Matrix API path");
        }

        URI uri = URI.create(baseUrl + path);
        if (!sameOrigin(baseUri, uri)) {
            throw new IllegalArgumentException("Request URI must remain on the configured homeserver");
        }
        return uri;
    }

    private static boolean sameOrigin(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected) == effectivePort(actual);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    public void setAccessToken(String accessToken) {
        if (accessToken != null && (accessToken.isBlank()
                || accessToken.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Access token must be nonblank and contain no control characters");
        }
        this.accessToken = accessToken;
    }

    public CompletableFuture<HttpResponse<String>> get(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        addAuthorization(builder);

        HttpRequest request = builder.GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);
    }

    public CompletableFuture<HttpResponse<String>> post(String path, String jsonBody) {
        Objects.requireNonNull(jsonBody, "JSON request body must not be null");
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        addAuthorization(builder);

        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::handleResponse);
    }

    private void addAuthorization(HttpRequest.Builder builder) {
        String token = accessToken;
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private HttpResponse<String> handleResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 400) {
            // Response bodies may contain credentials, tokens, or untrusted server content.
            logger.warn("Matrix API request failed with HTTP status {}.", status);
        } else {
            logger.debug("Matrix API request completed with HTTP status {}.", status);
        }
        return response;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
