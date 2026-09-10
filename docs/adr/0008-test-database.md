# ADR 0008: Local and CI database for tests

- **Status:** accepted
- **Date:** 2026-09-02
- **Accepted:** 2026-09-11 (SPEC-008 `/speckit-plan`)
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

Option 1. Testcontainers with `mysql:8.4`, a shared `@ServiceConnection` base
test class (`PersistenceIT`), and reuse enabled for fast local iteration. A
developer supplies a container runtime — **Colima**, or **Podman**
(`podman machine start` + `DOCKER_HOST` pointing at its socket; already installed
on the current dev box), or a local **MySQL 8.4** as the no-runtime fallback
(Option 2).

## Consequences

- One-time container-runtime setup per dev machine; noted in
  `specs/008-persistence-domain-services/quickstart.md` and the README.
- First integration-test run is slower (image pull).
- CI: Docker is available on `ubuntu-latest`, so Testcontainers works there too.
- Where the dev machine cannot start a runtime, the persistence integration
  tests are written and verified in CI; the unit-level tests (codecs,
  `CurrentEngineVersion`, ArchUnit) run everywhere.
