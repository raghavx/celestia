# ADR 0008: Local and CI database for tests

- **Status:** proposed
- **Date:** 2026-09-02
- **Decision ref:** D8 (PROJECT_PLAN.md §2)

## Context

Integration tests from Phase 3 onward need a real MySQL 8.x — Flyway migrations,
JSON columns, generated columns, and `utf8mb4` collation behave differently from
H2. Testcontainers is the idiomatic approach but needs a container runtime; the
dev machine currently has no Docker.

## Options considered

1. Testcontainers with `mysql:8.4`; devs install Colima (or Rancher Desktop /
   Podman); GitHub Actions uses Testcontainers or a `services:` MySQL container.
2. Developer-run local MySQL 8.4 plus a CI service container; no Testcontainers.
3. Embedded MariaDB4j — rejected (not MySQL; JSON and auth differences).

## Decision

_Proposed:_ Option 1. Testcontainers with `mysql:8.4`, a shared `@Testcontainers`
base test class, and reuse enabled for fast local iteration. Devs install Colima.
Option 2 is documented as the fallback for environments without a container
runtime.

## Consequences

- One-time Colima install per dev machine; note in the README.
- First integration-test run is slower (image pull).
- CI: Docker is available on `ubuntu-latest`, so Testcontainers works there too.
