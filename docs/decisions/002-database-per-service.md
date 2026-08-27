# ADR 002: Enforce Database Ownership per Service

Status: **Accepted**

## Context

Order, product, inventory, identity, payment, and notification data have different owners and consistency rules.

## Problem

A shared database enables convenient joins and ACID transactions but couples schemas, deployments, permissions, and service availability. It also makes ownership ambiguous.

## Options considered

1. One shared database/schema: operationally simple but creates a distributed monolith.
2. One PostgreSQL server with an isolated database/user per service: resource-efficient locally while enforcing logical ownership.
3. One physical PostgreSQL instance per service: strongest isolation but unnecessarily expensive for local development.

## Decision

Each persistent service owns a logical database and credentials. Local Docker Compose may place those databases on one PostgreSQL server. Production can isolate physical instances according to scale, security, and availability needs.

## Reasoning

Exclusive ownership prevents cross-service repository access and keeps service contracts explicit. Logical isolation is enough to teach and enforce the boundary locally without running seven PostgreSQL containers.

## Trade-offs

- Cross-service joins become API composition or denormalized snapshots.
- Multi-service ACID transactions are unavailable, requiring sagas and eventual consistency.
- Local operations are lighter than separate containers, but the single local server remains one failure domain.

## Consequences

- Flyway migrations are service-owned.
- Hibernate schema creation is disabled in favor of validation.
- Cross-service IDs have no database foreign keys.
- Backups, retention, and access policies can evolve per service.
