package com.celestia.channel.whatsapp;

import com.celestia.channel.whatsapp.OutboundMessage.InteractiveListMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TemplateMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.celestia.channel.whatsapp.SendResult.Failed;
import com.celestia.channel.whatsapp.graphapi.GraphApiClient;

/** The v1 (and, for now, only) {@link MessagingChannel} implementation. */
public final class WhatsAppMessagingChannel implements MessagingChannel {

    private final GraphApiClient client;
    private final WhatsAppTemplateCatalog templateCatalog;

    public WhatsAppMessagingChannel(GraphApiClient client, WhatsAppTemplateCatalog templateCatalog) {
        this.client = client;
        this.templateCatalog = templateCatalog;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        return switch (message) {
            case TextMessage t -> client.sendText(t.to(), t.body());
            case InteractiveListMessage l -> client.sendInteractiveList(l.to(), l.header(), l.bodyText(), l.sections());
            case TemplateMessage tm -> sendTemplate(tm);
        };
    }

    private SendResult sendTemplate(TemplateMessage tm) {
        return templateCatalog.resolve(tm.templateKey())
                .<SendResult>map(resolved -> client.sendTemplate(tm.to(), resolved, tm.params()))
                .orElseGet(() -> new Failed(
                        "TEMPLATE_NOT_FOUND", "no template registered for key: " + tm.templateKey(), false));
    }
}
