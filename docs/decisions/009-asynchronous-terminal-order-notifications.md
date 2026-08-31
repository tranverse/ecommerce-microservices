# ADR 009: Trigger Notifications from Terminal Order Events

Status: **Accepted and implemented.**

## Context

Customers should be notified after an order is confirmed or cancelled. Payment and Inventory outcomes are intermediate saga facts, while Order owns the customer-visible state machine.

## Options considered

1. Order calls Notification synchronously: immediate result, but provider latency and availability become part of order completion.
2. Notification consumes Payment outcomes: fewer Order events, but Payment cannot know whether the overall order workflow reached a valid terminal state.
3. Notification consumes terminal Order events: eventual delivery, clear authority, and no synchronous coupling to order completion.

## Decision

Notification consumes versioned `OrderConfirmed` and `OrderCancelled` events from `order.events.v1` in its own Kafka consumer group. It stores its own history and inbox in `notification_db`. The initial `SYSTEM` channel addresses an opaque customer ID; the service does not synchronously fetch a user email for each event.

## Consequences

- Order completion remains successful when Notification or its provider is unavailable.
- Notifications are eventually consistent and require visible delivery state.
- Exact and semantic duplicates must not create repeated delivery.
- A real external provider must support an idempotency key for the database/provider crash window.
- Email delivery requires a future explicit recipient-data contract or local projection rather than direct database access.
