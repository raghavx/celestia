package com.celestia.channel.whatsapp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Meta webhook JSON ⇄ {@link InboundMessage} (research.md §4). A delivery can
 * batch several messages, or be something else entirely (a status callback, a
 * ping) — walked payloads with no {@code messages[]} array contribute zero
 * entries, never an error (FR-015).
 */
public final class InboundPayloadParser {

    private InboundPayloadParser() {}

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /** Every message in the delivery, parsed. */
    public static List<InboundMessage> parseAll(byte[] rawBody) {
        return extractMessageNodes(rawBody).stream().map(InboundPayloadParser::toInboundMessage).toList();
    }

    /**
     * Every message's own JSON object, in the same order as {@link #parseAll} —
     * what the caller stores as {@code raw_ref} so a single message can be
     * re-parsed later via {@link #parseOne} without re-walking the batch.
     */
    public static List<String> extractRawMessageJson(byte[] rawBody) {
        return extractMessageNodes(rawBody).stream().map(JsonNode::toString).toList();
    }

    /** Re-parses one message's JSON object (as stored by {@link #extractRawMessageJson}). */
    public static InboundMessage parseOne(String messageJson) {
        return toInboundMessage(MAPPER.readTree(messageJson));
    }

    private static List<JsonNode> extractMessageNodes(byte[] rawBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(rawBody);
        } catch (RuntimeException notJson) {
            return List.of();
        }
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode messages = change.path("value").path("messages");
                if (messages.isArray()) {
                    messages.forEach(out::add);
                }
            }
        }
        return out;
    }

    private static InboundMessage toInboundMessage(JsonNode node) {
        String providerId = node.path("id").asString();
        String from = normaliseE164(node.path("from").asString());
        Instant timestamp = Instant.ofEpochSecond(node.path("timestamp").asLong(0));
        String type = node.path("type").asString("");
        MessageContent content = "text".equals(type)
                ? new MessageContent.TextContent(node.path("text").path("body").asString(""))
                : new MessageContent.UnsupportedContent(type.isBlank() ? "unknown" : type);
        return new InboundMessage(providerId, from, timestamp, content);
    }

    private static String normaliseE164(String phone) {
        return (phone == null || phone.isBlank() || phone.startsWith("+")) ? phone : "+" + phone;
    }
}
