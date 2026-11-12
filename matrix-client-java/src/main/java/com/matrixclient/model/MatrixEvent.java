package com.matrixclient.model;

import java.time.Instant;

/**
 * Represents a single message (event) in a Matrix timeline.
 */
public record MatrixEvent(String eventId, String sender, String content, Instant timestamp) {
}
