package com.celestia.agent.admin;

import com.celestia.agent.admin.SeedOwnerBirthDataCommand.OwnerSeed;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The administrative actions (SPEC-012 FR-019).
 *
 * <p>Top-level rather than nested inside {@link SeedOwnerBirthDataCommand}: a
 * nested {@code @Configuration} inside a {@code @Component} is <b>not</b> picked
 * up by component scan, so the runner would never have been registered and the
 * seed would have silently done nothing.
 */
@Configuration(proxyBeanMethods = false)
public class AdminConfig {

    /**
     * Seeds the owner's birth data at startup, <b>only when explicitly asked
     * for</b> — so an ordinary boot never writes to {@code birth_data}.
     */
    @Bean
    ApplicationRunner seedOwnerBirthData(SeedOwnerBirthDataCommand command, OwnerSeed seed) {
        return args -> {
            if (seed.enabled()) {
                command.seed(seed);
            }
        };
    }
}
