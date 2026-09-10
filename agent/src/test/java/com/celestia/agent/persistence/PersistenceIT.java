package com.celestia.agent.persistence;

import com.celestia.agent.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base for persistence integration tests — a full context against a real
 * {@code mysql:8.4} with Flyway applied (ADR-0008). Subclasses inject the
 * {@code store} services.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class PersistenceIT {
}
