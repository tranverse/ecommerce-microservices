# API Gateway

API Gateway is the public HTTP entry point for the currently implemented services. It routes requests, validates access tokens at the edge, applies coarse route authorization, propagates a correlation ID, removes caller-supplied identity headers, and enforces bounded downstream timeouts. It owns no database and contains no e-commerce business rules.

## Why a Gateway?

Without a gateway, every client must know each service address and repeat cross-cutting concerns such as token handling and request correlation. A gateway gives clients one stable origin and rejects obviously invalid traffic before it consumes backend capacity. Backend services still validate tokens and enforce domain authorization: the network and the gateway are not trusted as proof of identity.

```text
Client -> API Gateway -> Auth / User / Product / Inventory
             |
             +-> validate JWT, authorize route, normalize correlation ID

Auth -> auth_db       User -> user_db
Product -> product_db Inventory -> inventory_db
Gateway -> no database
```

## Route and Edge-Authorization Matrix

| Request | Edge policy | Downstream |
| --- | --- | --- |
| `/api/v1/auth/**` | Public | Auth Service |
| `/.well-known/jwks.json` | Public | Auth Service |
| `GET /api/v1/products/**` | Public | Product Service |
| `/api/v1/users/**` | `CUSTOMER` or `ADMIN` | User Service |
| Non-GET `/api/v1/products/**` | `ADMIN` | Product Service |
| `/api/v1/inventory/**` | `ADMIN` | Inventory Service |
| `/actuator/health/**`, `/actuator/info` | Public, handled locally | Gateway |

The bearer token is relayed unchanged so a protected downstream service can verify it independently. `X-User-Id` and `X-User-Roles` from callers are removed; identity must come from a verified JWT, never an untrusted header.

## Request Behavior

- Only RS256 access tokens from the configured exact issuer are accepted.
- JWKS lookup has independent 2-second connection/read timeouts by default.
- Downstream connection timeout is 2 seconds and response timeout is 5 seconds by default.
- A safe `X-Correlation-ID` (`A-Z`, `a-z`, digits, `.`, `_`, `:`, `-`, maximum 128 characters) is preserved; otherwise a UUID is generated.
- The response contains exactly one normalized correlation ID even when the downstream service also returns it.
- Unauthenticated and forbidden edge responses use stable sanitized JSON and do not expose exception details.
- Secure response headers are applied and the `Server` response header is removed.

Rate limiting, CORS for a real web origin, TLS termination, retry/circuit-breaker policy, and centralized telemetry are intentionally deferred until their deployment or failure scenario is implemented.

## Configuration

| Variable | Required | Default |
| --- | --- | --- |
| `SERVER_PORT` | No | `8080` |
| `GATEWAY_AUTH_ISSUER` | No | `http://localhost:8081` |
| `GATEWAY_AUTH_JWKS_URI` | No | `http://localhost:8081/.well-known/jwks.json` |
| `GATEWAY_JWKS_CONNECT_TIMEOUT` | No | `PT2S` |
| `GATEWAY_JWKS_READ_TIMEOUT` | No | `PT2S` |
| `GATEWAY_CONNECT_TIMEOUT_MS` | No | `2000` |
| `GATEWAY_RESPONSE_TIMEOUT` | No | `5s` |
| `AUTH_SERVICE_URL` | No | `http://localhost:8081` |
| `USER_SERVICE_URL` | No | `http://localhost:8082` |
| `PRODUCT_SERVICE_URL` | No | `http://localhost:8083` |
| `INVENTORY_SERVICE_URL` | No | `http://localhost:8084` |

Use internal DNS names such as `http://auth-service:8081` inside Docker networking. The issuer configured in Auth, Gateway, and resource services must match exactly.

## Build and Test

Start the downstream services, then run Gateway:

```powershell
.\mvnw.cmd -pl services/api-gateway spring-boot:run
```

Run its test suite and build the production-style image:

```powershell
.\mvnw.cmd -pl services/api-gateway test
docker build -f services/api-gateway/Dockerfile -t ecommerce/api-gateway:local .
```

Gateway has 8 tests covering public routing, protected-route rejection before forwarding, role policy, bearer-token relay, spoofed-header removal, invalid tokens, safe/unsafe correlation IDs, and response-header normalization against a real Reactor Netty downstream server.

The image uses a multi-stage Java 21 build, a JRE-only runtime, a non-root `spring` user, graceful shutdown, and readiness health check.
