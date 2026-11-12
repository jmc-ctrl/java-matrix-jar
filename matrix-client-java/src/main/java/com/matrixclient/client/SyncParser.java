package com.matrixclient.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.matrixclient.model.MatrixEvent;
import com.matrixclient.model.RoomUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the raw JSON response from the Matrix /sync endpoint into typed models.
 */
public class SyncParser {
    private static final Logger logger = LoggerFactory.getLogger(SyncParser.class);
    private final ObjectMapper objectMapper;

    public SyncParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public List<RoomUpdate> parseRoomUpdates(JsonNode syncNode) {
        List<RoomUpdate> updates = new ArrayList<>();
        JsonNode roomsNode = syncNode.get("rooms");
        
        if (roomsNode == null || !roomsNode.has("join")) return updates;

        JsonNode joinedRooms = roomsNode.get("join");
        for (JsonNode roomNode : joinedRooms) {
            String roomId = roomNode.get("room_id").asText();
            List<MatrixEvent> events = parseEvents(roomNode.get("timeline"));
            updates.add(new RoomUpdate(roomId, events, true));
        }
        
        // Handle other room states (leave, invite) if necessary
        return updates;
    }

    private List<MatrixEvent> parseEvents(JsonNode timelineNode) {
        List<MatrixEvent> events = new ArrayList<>();
        if (timelineNode == null || !timelineNode.isArray()) return events;

        for (JsonNode eventNode : timelineNode) {
            if (eventNode.has("content") && eventNode.has("origin_server_ts")) {
                String content = eventNode.get("content").get("body").asText("");
                String sender = eventNode.get("sender").asText();
                long ts = eventNode.get("origin_server_ts").asLong();
                String eventId = eventNode.get("event_id").asText();
                
                events.add(new MatrixEvent(eventId, sender, content, Instant.ofEpochMilli(ts)));
            }
        }
        return events;
    }
}
