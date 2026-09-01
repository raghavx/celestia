# ADR 0007: JDK version

- **Status:** accepted (implemented in commit 37476b8)
- **Date:** 2026-09-02
- **Decision ref:** D7 (PROJECT_PLAN.md §2)

## Context

Spring Boot 4's baseline is Java 17. The current LTS is Java 25, already installed
on the dev machine. Some build tooling lags new JDKs — already hit with
Spotless / palantir-java-format and ArchUnit 1.4.

## Options considered

1. Java 17 LTS — safest tooling, oldest language level.
2. Java 21 LTS — middle ground.
3. Java 25 LTS — newest features, occasional tooling friction.

## Decision

**Java 25 LTS**, `maven.compiler.release=25`. The tooling-lag cost is accepted and
managed: ArchUnit is pinned to 1.5.0 (reads JDK 25 bytecode); Spotless runs
whitespace/EOL only until palantir-java-format ships JDK 25 support (tracked TODO
in the parent POM). Maven Toolchains configuration is deferred until a second JDK
is actually needed.

## Consequences

- Newest language features are available.
- Occasional pressure to bump dependency versions for JDK 25 compatibility.
- CI must run on JDK 25 (`temurin` 25 in the workflow).
