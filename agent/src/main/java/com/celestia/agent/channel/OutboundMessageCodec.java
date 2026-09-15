package com.celestia.agent.channel;

import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage.InteractiveListMessage;
import com.celestia.channel.whatsapp.OutboundMessage.ListRow;
import com.celestia.channel.whatsapp.OutboundMessage.ListSection;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateParam;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code outbox_message.payload_json} ⇄ {@link OutboundMessage} — the queue's
 * own storage format, not a Graph API shape (that mapping is
 * {@code WhatsAppMessagingChannel}'s, internal to {@code channel-whatsapp}).
 */
@Component
public class OutboundMessageCodec {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public String write(OutboundMessage message) {
        ObjectNode root = MAPPER.createObjectNode();
        switch (message) {
            case TextMessage t -> {
                root.put("kind", "text");
                root.put("to", t.to());
                root.put("body", t.body());
            }
            case InteractiveListMessage l -> {
                root.put("kind", "interactive-list");
                root.put("to", l.to());
                root.put("header", l.header());
                root.put("bodyText", l.bodyText());
                ArrayNode sections = root.putArray("sections");
                for (ListSection s : l.sections()) {
                    ObjectNode sn = sections.addObject();
                    sn.put("title", s.title());
                    ArrayNode rows = sn.putArray("rows");
                    for (ListRow r : s.rows()) {
                        ObjectNode rn = rows.addObject();
                        rn.put("id", r.id());
                        rn.put("title", r.title());
                        rn.put("description", r.description());
                    }
                }
            }
            case TemplateMessage tm -> {
                root.put("kind", "template");
                root.put("to", tm.to());
                root.put("templateKey", tm.templateKey());
                ArrayNode params = root.putArray("params");
                for (TemplateParam p : tm.params()) {
                    ObjectNode pn = params.addObject();
                    pn.put("name", p.name());
                    pn.put("value", p.value());
                }
            }
        }
        return MAPPER.writeValueAsString(root);
    }

    public OutboundMessage read(String json) {
        JsonNode root = MAPPER.readTree(json);
        String kind = root.path("kind").asString("");
        return switch (kind) {
            case "text" -> new TextMessage(root.path("to").asString(), root.path("body").asString());
            case "interactive-list" -> new InteractiveListMessage(
                    root.path("to").asString(),
                    root.path("header").asString(),
                    root.path("bodyText").asString(),
                    readSections(root.path("sections")));
            case "template" -> new TemplateMessage(
                    root.path("to").asString(), root.path("templateKey").asString(), readParams(root.path("params")));
            default -> throw new IllegalArgumentException("unknown outbound message kind: " + kind);
        };
    }

    private static List<ListSection> readSections(JsonNode sections) {
        List<ListSection> out = new ArrayList<>();
        for (JsonNode s : sections) {
            List<ListRow> rows = new ArrayList<>();
            for (JsonNode r : s.path("rows")) {
                rows.add(new ListRow(r.path("id").asString(), r.path("title").asString(),
                        r.path("description").asString("")));
            }
            out.add(new ListSection(s.path("title").asString(), rows));
        }
        return out;
    }

    private static List<TemplateParam> readParams(JsonNode params) {
        List<TemplateParam> out = new ArrayList<>();
        for (JsonNode p : params) {
            out.add(new TemplateParam(p.path("name").asString(), p.path("value").asString("")));
        }
        return out;
    }
}
