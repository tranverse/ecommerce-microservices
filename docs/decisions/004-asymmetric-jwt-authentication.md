# ADR 004: Asymmetric JWT Authentication

## Status

Accepted and implemented in Auth Service. Gateway and resource-service enforcement are pending their implementation milestones.

## Context

Clients need to authenticate once, then call independently deployed services. Calling Auth synchronously for every request would make it a latency and availability bottleneck. Giving every service a signing secret would make every verifier capable of forging identities.

## Options Considered

1. Server-side sessions in a shared store.
2. Opaque access tokens with Auth introspection on every request.
3. HMAC-signed JWT shared across services.
4. RSA-signed JWT with public JWKS and stateful opaque refresh tokens.

## Decision

Use short-lived RS256 JWT access tokens. Auth alone owns the private key and publishes public keys through JWKS. Use rotating 256-bit opaque refresh tokens; store only SHA-256 hashes in `auth_db`.

## Reasoning

Resource services can validate access tokens locally during an Auth outage. Asymmetric keys separate signing authority from verification. Stateful refresh tokens provide logout/rotation without placing long-lived bearer authority in a JWT. Short access-token TTL bounds most revocation delay.

## Trade-offs

- JWT claims can be stale until expiry and immediate access-token revocation is not implemented.
- Key rotation and clock synchronization become operational responsibilities.
- Access tokens are larger than opaque identifiers.
- Refresh-token storage and cleanup remain stateful.
- A direct standalone local process may use an ephemeral key; the full Compose baseline requires an ignored stable local key so routine Auth restarts preserve issued tokens.

## Consequences

- Services must validate issuer, signature, expiry, roles, and ownership rather than trust decoded text.
- Private keys enter only through production secret management.
- JWKS must publish overlap keys during rotation.
- Tokens must stay out of URLs/logs and use TLS in deployed environments.
- Auth and User remain separate data owners even though the JWT subject links their identities.
