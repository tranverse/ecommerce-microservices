# ADR 001: Use a Monorepo with Independently Deployable Services

Status: **Accepted**

## Context

The repository is both a working system and a guided microservices learning resource. Services must remain independently runnable while documentation and local infrastructure evolve coherently.

## Problem

We need repository organization that makes cross-service learning, atomic infrastructure changes, and end-to-end development approachable without turning all services into one deployable application.

## Options considered

1. One repository per service: strongest repository isolation, but high setup and documentation overhead for this project.
2. Monorepo with independent Maven projects: easier navigation and coordinated local development, with discipline required to preserve service boundaries.
3. One Spring Boot application with modules: simplest build, but it is a modular monolith rather than the required distributed runtime.

## Decision

Use a monorepo. Application services live under `services/`, infrastructure under `infrastructure/`, and documentation under `docs/`. A root Maven aggregator may coordinate builds, but every service produces its own executable artifact and Docker image.

## Reasoning

The learning experience benefits from seeing APIs, events, infrastructure, and docs together. Independent service artifacts, databases, and processes preserve runtime boundaries regardless of repository layout.

## Trade-offs

- Easier refactoring and end-to-end setup, but repository-wide changes can accidentally couple services.
- Shared dependency management improves consistency, but services are upgraded together more often.
- CI can build everything initially; path-based optimization may be added only when build time justifies it.

## Consequences

- No shared business-domain module is allowed.
- Root build configuration must not create runtime coupling.
- Service-specific Flyway migrations, configuration, tests, and Dockerfiles remain inside each service.
