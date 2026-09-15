package com.celestia.channel.whatsapp;

/** The content of one inbound WhatsApp message. */
public sealed interface MessageContent permits MessageContent.TextContent, MessageContent.UnsupportedContent {

    record TextContent(String body) implements MessageContent {
        public TextContent {
            if (body == null) {
                throw new IllegalArgumentException("body must not be null");
            }
        }
    }

    /** A message type this channel does not specifically understand yet (image, audio, location, …). */
    record UnsupportedContent(String type) implements MessageContent {
        public UnsupportedContent {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("type must not be blank");
            }
        }
    }
}
