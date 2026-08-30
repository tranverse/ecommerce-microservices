# ADR 005: Subject-Owned User Profiles

## Status

Accepted and implemented in User Service. Event-driven automatic profile provisioning remains pending.

## Context

Auth and User represent the same human for different purposes. Auth owns sensitive credentials and issues a stable UUID subject. User owns mutable business profile data and addresses. APIs must prevent a valid customer token from selecting another customer's resources.

## Options Considered

1. Put credentials and profiles back into one shared database/table.
2. Accept `userId` in profile paths and compare it with JWT `sub` in every handler.
3. Trust a user ID inserted by Gateway.
4. Expose subject-relative `/me` resources and derive identity from a JWT validated again by User.

## Decision

Use the Auth account UUID as JWT `sub` and User profile primary key. Customer self-service endpoints are rooted at `/api/v1/users/me`; controllers never accept a customer-selectable profile ID. User validates RS256/issuer/expiry/roles through Auth JWKS and applies address ownership inside the profile aggregate.

## Reasoning

The stable subject links data without shared persistence. `/me` makes the secure path the simplest path and removes repeated equality checks vulnerable to omission. Backend validation provides defense in depth when traffic bypasses or misconfigures Gateway. Starting address lookup from the subject-owned aggregate avoids insecure direct object reference behavior and ownership disclosure.

## Trade-offs

- Administrative cross-user APIs will need separate, role-specific endpoints and audit controls.
- User depends on JWKS availability on cache miss/key rotation, so retrieval uses explicit timeouts.
- Credential registration and profile creation are temporarily two operations.
- Subject IDs become a long-lived cross-service contract and must never be recycled.

## Consequences

- `user_db` stores no password or credential email.
- Auth never writes User tables.
- Unknown and foreign addresses both return `404 ADDRESS_NOT_FOUND`.
- Kafka can later provision profiles using the same subject without changing ownership.
- Gateway claims are advisory until the receiving service validates and authorizes them.
