package com.matrixclient.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single update to a room (e.g., new event, membership change).
 */
public record RoomUpdate(
    String roomId,
    List<MatrixEvent> newEvents,
    boolean isJoined
) {}
