# ADR 0006: Build tooling

- **Status:** accepted (implemented in commit 37476b8)
- **Date:** 2026-09-02
- **Decision ref:** D6 (PROJECT_PLAN.md §2)

## Context

A multi-module JVM project mixing pure-Java libraries with one Spring Boot
application. Builds must be reproducible on any machine. The dev machine's system
Maven is 3.6.3, which predates Spring Boot 4's Maven ≥ 3.9 requirement and has
known issues on JDK 25.

## Options considered

1. Maven, multi-module, with the Maven Wrapper.
2. Gradle (better multi-module ergonomics, Kotlin DSL).
3. Rely on a system-installed build tool.

## Decision

**Maven, multi-module, with the Maven Wrapper** pinned to 3.9.11
(`distributionType=only-script`). The parent POM does **not** inherit
`spring-boot-starter-parent`; it imports `spring-boot-dependencies` and
`spring-ai-bom` in `dependencyManagement`, so the pure modules stay Spring-free.
Plugin versions are pinned in `pluginManagement`.

## Consequences

- Contributors need only a JDK; `./mvnw` provides Maven.
- CI runs `./mvnw verify`.
- Gradle's nicer multi-module story is foregone for Maven's ubiquity in the Spring
  ecosystem and simpler BOM import.
