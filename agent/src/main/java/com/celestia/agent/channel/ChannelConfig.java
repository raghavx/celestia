package com.celestia.agent.channel;

import com.celestia.channel.whatsapp.WhatsAppMessagingChannelConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Registers {@link WhatsAppMessagingChannelConfig} (which itself registers
 * {@code WhatsAppConfig} and the {@code MessagingChannel} bean) and turns on
 * {@code @Scheduled} for the inbound worker and outbound sender.
 * {@code channel-whatsapp} is not component-scanned by
 * {@code @SpringBootApplication}, so this import is required.
 */
@Configuration(proxyBeanMethods = false)
@Import(WhatsAppMessagingChannelConfig.class)
@EnableScheduling
public class ChannelConfig {}
