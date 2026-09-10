package com.celestia.agent;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * A shared {@code mysql:8.4} container (ADR-0008). {@code @ServiceConnection}
 * wires Spring's datasource to it — no manual URL/credentials. Context caching
 * gives every {@code @Import}-ing test the same container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4").withReuse(true);
    }
}
