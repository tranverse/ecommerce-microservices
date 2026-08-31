# ADR 003: Use an Orchestrated Saga for Order Processing

Status: **Accepted; implemented through terminal confirmation and payment-failure compensation**

## Context

Completing an order requires local state changes in Order, Inventory, and Payment services. Notification reacts after the business outcome.

## Problem

No single database transaction can atomically reserve inventory, process payment, and update an order across independent services. Failures require durable progress, valid state transitions, and compensation.

## Options considered

1. Distributed two-phase commit: strong atomicity but poor fit for Kafka, operational complexity, and tight participant coupling.
2. Choreographed saga: services react to events without a central coordinator; fewer central responsibilities but workflow understanding and change become distributed across consumers.
3. Orchestrated saga: Order Service records saga state and sends commands based on participant outcomes; clearer control flow with a coordinator dependency.

## Decision

Order Service will orchestrate the saga. It publishes inventory and payment commands through a transactional outbox, consumes outcome events idempotently, applies an explicit state machine, and emits compensation commands when necessary.

## Reasoning

Order already owns the customer-visible lifecycle. Centralizing transition decisions there makes failure paths, timeouts, and compensation understandable and testable. It is the better teaching and operational choice for this bounded workflow.

## Trade-offs

- Order Service contains more workflow logic and must remain highly observable.
- Participants remain decoupled from the full workflow but depend on command/event contracts.
- The workflow is eventually consistent; clients receive a pending state and query progress.

## Consequences

- Inventory and Payment commands must be idempotent.
- Order state transitions must reject stale or invalid events.
- Payment failure after reservation triggers inventory release and cancellation.
- Transactional outbox and processed-event records are required where state and messaging consistency matter.
- A sequence diagram and failure matrix will be maintained with the implementation.
