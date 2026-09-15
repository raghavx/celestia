package com.celestia.agent.persistence;

import com.celestia.agent.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for persistence integration tests — a full context against a real
 * {@code mysql:8.4} with Flyway applied (ADR-0008). Subclasses inject the
 * {@code store} services. The {@code test} profile (see
 * {@code application-test.yml}) stretches the WhatsApp channel's
 * {@code @Scheduled} poll interval out to an hour — every IT here drives the
 * worker/sender explicitly via {@code tick()}, so a live ~2s background
 * poller only races the DB against explicit test assertions and outlives
 * Testcontainers during JVM shutdown (SPEC-009).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class PersistenceIT {
}
