package com.matrixclient.model;

import java.util.List;

/**
 * Represents a room in the Matrix client.
 */
public record Room(String roomId, String displayName, boolean isJoined) {
    public static Room fromJson(com.fasterxml.jackson.databind.JsonNode node) {
        return new Room(
            node.get("room_id").asText(),
            node.has("name") ? node.get("name").asText() : "Unknown Room",
            node.get("state").get("joined").asBoolean()
        );
    }
}
