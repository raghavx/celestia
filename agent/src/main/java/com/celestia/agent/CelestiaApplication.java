package com.celestia.agent;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * {@code @ConfigurationPropertiesScan} registers the {@code @ConfigurationProperties}
 * records that no {@code @Configuration} enables explicitly — SPEC-012's
 * {@code EntitlementProperties} and {@code OwnerSeed}. Without it the application
 * fails to start, which is how it was found: a properties record is only a bean if
 * something registers it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CelestiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CelestiaApplication.class, args);
    }

    /**
     * The application's clock, so "now" is a parameter rather than ambient
     * (SPEC-012 FR-020b). Constitution II's no-ambient-clock rule binds the pure
     * modules, not {@code agent} — but the turn-record purge is only testable
     * without waiting ninety days if time is injected, so it is injected here.
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
