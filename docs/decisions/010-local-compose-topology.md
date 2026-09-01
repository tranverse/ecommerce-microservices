# ADR 010: Use an Explicit Local Compose Topology

Status: **Accepted**

## Context

The connected order workflow requires eight Spring Boot applications, PostgreSQL, Kafka, versioned topics, service-specific credentials, health checks, and stable internal addresses. Manual startup no longer represents a repeatable integration environment.

## Options considered

1. Continue running each dependency manually: low initial effort but error-prone and difficult to reproduce.
2. Use Docker Compose with one container per application, one local PostgreSQL server containing isolated databases/users, and one Kafka KRaft broker: reproducible while remaining affordable on a developer machine.
3. Introduce Kubernetes now: closer to a production scheduler but adds operational concepts before runtime behavior is validated locally.

## Decision

Use `compose.yaml` as the local integration topology. Application containers communicate on one private Compose network using service-name DNS. Host ports bind to loopback for development only. PostgreSQL hosts seven isolated logical databases and roles. Kafka exposes separate internal and host listeners, disables automatic topic creation, and uses a one-shot initializer for versioned topics and DLTs.

Compose health conditions order initial startup. They do not provide runtime retry or circuit breaking.

## Consequences

- One command can build and start the complete workflow.
- Local application configuration demonstrates real container DNS instead of `localhost` assumptions.
- Database ownership remains enforceable without seven PostgreSQL processes.
- The local broker and database are single-node failure domains and do not model production high availability.
- Direct backend host ports are debugging conveniences and must not be treated as a production exposure model.
- Kubernetes remains deferred until health, resilience, and observability behavior is exercised in Compose.
