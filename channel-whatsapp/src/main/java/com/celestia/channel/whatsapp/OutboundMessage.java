package com.celestia.channel.whatsapp;

import java.util.List;

/**
 * A message to send through {@link MessagingChannel}. Free-form ({@link TextMessage},
 * {@link InteractiveListMessage}) is only valid inside a recipient's 24h
 * customer-service window; {@link TemplateMessage} is valid regardless — the
 * caller ({@code WhatsAppOutboundSender}) decides which to send, not the port.
 */
public sealed interface OutboundMessage
        permits OutboundMessage.TextMessage, OutboundMessage.InteractiveListMessage, OutboundMessage.TemplateMessage {

    /** E.164 recipient. */
    String to();

    record TextMessage(String to, String body) implements OutboundMessage {
        public TextMessage {
            requireNonBlank(to, "to");
            requireNonBlank(body, "body");
        }
    }

    record InteractiveListMessage(String to, String header, String bodyText, List<ListSection> sections)
            implements OutboundMessage {
        public InteractiveListMessage {
            requireNonBlank(to, "to");
            requireNonBlank(header, "header");
            requireNonBlank(bodyText, "bodyText");
            if (sections == null || sections.isEmpty()) {
                throw new IllegalArgumentException("sections must not be empty");
            }
            sections = List.copyOf(sections);
        }
    }

    record ListSection(String title, List<ListRow> rows) {
        public ListSection {
            requireNonBlank(title, "title");
            if (rows == null || rows.isEmpty()) {
                throw new IllegalArgumentException("rows must not be empty");
            }
            rows = List.copyOf(rows);
        }
    }

    record ListRow(String id, String title, String description) {
        public ListRow {
            requireNonBlank(id, "id");
            requireNonBlank(title, "title");
            description = description == null ? "" : description;
        }
    }

    record TemplateMessage(String to, String templateKey, List<TemplateParam> params)
            implements OutboundMessage {
        public TemplateMessage {
            requireNonBlank(to, "to");
            requireNonBlank(templateKey, "templateKey");
            params = params == null ? List.of() : List.copyOf(params);
        }
    }

    record TemplateParam(String name, String value) {
        public TemplateParam {
            requireNonBlank(name, "name");
            value = value == null ? "" : value;
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
