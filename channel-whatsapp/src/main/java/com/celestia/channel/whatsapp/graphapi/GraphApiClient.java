package com.celestia.channel.whatsapp.graphapi;

import com.celestia.channel.whatsapp.OutboundMessage.ListRow;
import com.celestia.channel.whatsapp.OutboundMessage.ListSection;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateParam;
import com.celestia.channel.whatsapp.ResolvedTemplate;
import com.celestia.channel.whatsapp.SendResult;
import com.celestia.channel.whatsapp.SendResult.Failed;
import com.celestia.channel.whatsapp.SendResult.Sent;
import com.celestia.channel.whatsapp.WhatsAppConfig;
import java.util.List;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The WhatsApp Graph API HTTP client — internal to {@code channel-whatsapp}
 * (ArchUnit-enforced); {@link com.celestia.channel.whatsapp.WhatsAppMessagingChannel}
 * is the only caller. {@code POST /{phoneNumberId}/messages}.
 */
public final class GraphApiClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final RestClient restClient;
    private final WhatsAppConfig config;

    public GraphApiClient(WhatsAppConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.graphApiBaseUrl() + "/" + config.graphApiVersion())
                .defaultHeader("Authorization", "Bearer " + config.accessToken())
                .build();
    }

    public SendResult sendText(String to, String body) {
        ObjectNode root = envelope(to, "text");
        root.putObject("text").put("body", body);
        return post(root);
    }

    public SendResult sendInteractiveList(String to, String header, String bodyText, List<ListSection> sections) {
        ObjectNode root = envelope(to, "interactive");
        ObjectNode interactive = root.putObject("interactive");
        interactive.put("type", "list");
        interactive.putObject("header").put("type", "text").put("text", header);
        interactive.putObject("body").put("text", bodyText);
        ObjectNode action = interactive.putObject("action");
        action.put("button", "Choose");
        ArrayNode sectionsNode = action.putArray("sections");
        for (ListSection s : sections) {
            ObjectNode sn = sectionsNode.addObject();
            sn.put("title", s.title());
            ArrayNode rows = sn.putArray("rows");
            for (ListRow r : s.rows()) {
                ObjectNode rn = rows.addObject();
                rn.put("id", r.id());
                rn.put("title", r.title());
                rn.put("description", r.description());
            }
        }
        return post(root);
    }

    public SendResult sendTemplate(String to, ResolvedTemplate template, List<TemplateParam> params) {
        ObjectNode root = envelope(to, "template");
        ObjectNode t = root.putObject("template");
        t.put("name", template.metaTemplateName());
        t.putObject("language").put("code", template.languageCode());
        if (!params.isEmpty()) {
            ArrayNode components = t.putArray("components");
            ObjectNode body = components.addObject();
            body.put("type", "body");
            ArrayNode parameters = body.putArray("parameters");
            for (TemplateParam p : params) {
                parameters.addObject().put("type", "text").put("text", p.value());
            }
        }
        return post(root);
    }

    private ObjectNode envelope(String to, String type) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("messaging_product", "whatsapp");
        root.put("to", stripPlus(to));
        root.put("type", type);
        return root;
    }

    private SendResult post(ObjectNode body) {
        try {
            JsonNode response = restClient.post()
                    .uri("/{phoneNumberId}/messages", config.phoneNumberId())
                    .body(MAPPER.writeValueAsString(body))
                    .retrieve()
                    .body(JsonNode.class);
            String providerMessageId = response == null
                    ? null
                    : response.path("messages").path(0).path("id").asString(null);
            if (providerMessageId == null || providerMessageId.isBlank()) {
                return new Failed("EMPTY_RESPONSE", "Graph API response had no message id", true);
            }
            return new Sent(providerMessageId);
        } catch (HttpStatusCodeException e) {
            boolean retryable = e.getStatusCode().is5xxServerError();
            String message = extractErrorMessage(e.getResponseBodyAsString());
            return new Failed(String.valueOf(e.getStatusCode().value()), message, retryable);
        } catch (ResourceAccessException networkError) {
            return new Failed("NETWORK_ERROR", networkError.getMessage(), true);
        }
    }

    private static String extractErrorMessage(String body) {
        try {
            return MAPPER.readTree(body).path("error").path("message").asString(body);
        } catch (RuntimeException notJson) {
            return body;
        }
    }

    private static String stripPlus(String e164) {
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }
}
