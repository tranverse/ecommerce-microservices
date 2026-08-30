# ADR 006: Gateway Edge Security with Backend Defense in Depth

## Status

Accepted and implemented for API Gateway, User Service, and Order Service. Resource-server enforcement for other protected business services remains incremental.

## Context

Clients need one public origin, while services need protection from invalid traffic and caller-spoofed identity. Relying only on the Gateway creates a dangerous bypass: a direct call, routing mistake, compromised internal workload, or future alternate ingress could reach an unprotected backend.

## Options Considered

1. Expose every service directly and make clients discover service addresses.
2. Validate JWT only at Gateway and forward trusted user headers to otherwise unprotected services.
3. Validate JWT only in each service and use Gateway purely as a transparent router.
4. Validate at Gateway for coarse route policy, relay the bearer token, and validate/authorize again in each protected service.

## Decision

Choose option 4. Gateway is the public entry point, verifies RS256 tokens against Auth JWKS, applies coarse role checks, removes caller-provided `X-User-Id`/`X-User-Roles`, and relays the original bearer token. Protected services verify the token independently and enforce their own role, ownership, and state-transition rules.

Gateway owns no database and no domain policy. Correlation IDs are the only request metadata it deliberately creates or normalizes. Downstream calls use explicit connection and response timeouts; automatic write retries are not enabled.

## Reasoning

Edge validation rejects bad requests early and centralizes client-facing routing. Backend validation makes authorization survive gateway bypass and keeps business policy next to the data it protects. RS256 lets multiple verifiers use public material without distributing Auth's signing capability. Relaying the token preserves a cryptographically verifiable identity instead of converting it into forgeable headers.

## Trade-offs

- JWT verification and security configuration are duplicated across Gateway and protected services.
- Gateway and backends each maintain a JWKS cache and may independently fail during key discovery.
- A gateway is a critical availability boundary and must eventually run multiple instances behind a load balancer.
- Coarse route rules can drift from backend rules, so tests and route documentation must be maintained.
- Token relay exposes the access token to intended backend services; logs and tracing must redact Authorization headers.

## Consequences

- A valid edge decision never substitutes for backend ownership checks.
- Internal network location is not authentication.
- Identity headers from external callers are stripped and are not an authority source.
- Gateway failures are bounded by timeouts; retries/circuit breakers require operation-specific justification.
- Product, Inventory, Payment, Notification, and later protected services must implement their own protection before their protected endpoints are considered safely deployable.
