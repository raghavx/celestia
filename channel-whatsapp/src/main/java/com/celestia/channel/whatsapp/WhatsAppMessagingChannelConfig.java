package com.celestia.channel.whatsapp;

import com.celestia.channel.whatsapp.graphapi.GraphApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link MessagingChannel} bean, keeping {@link GraphApiClient}
 * construction inside {@code com.celestia.channel.whatsapp} so callers in
 * {@code agent} never need to depend on the internal {@code graphapi}
 * package (FR-011, {@code ChannelArchitectureTest}). Imported explicitly by
 * {@code agent.channel.ChannelConfig} — {@code channel-whatsapp} is not
 * component-scanned by {@code @SpringBootApplication}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WhatsAppConfig.class)
public class WhatsAppMessagingChannelConfig {

    @Bean
    public MessagingChannel messagingChannel(WhatsAppConfig config) {
        return new WhatsAppMessagingChannel(new GraphApiClient(config), WhatsAppTemplateCatalog.loadFromClasspath());
    }
}
