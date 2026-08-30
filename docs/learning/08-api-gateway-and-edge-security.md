# API Gateway and Edge Security

## What Problem Does It Solve?

An API Gateway gives external clients one address while the system contains many independently deployed services. It performs edge concerns that apply across routes: routing, coarse authentication/authorization, correlation IDs, secure headers, and bounded network calls.

It should not decide whether an address belongs to a user, whether stock may be released, or whether an order transition is legal. Those rules belong to the services that own the data.

## Why Microservices Need It

Without a gateway, clients become coupled to service topology and every backend becomes a public perimeter. Moving or scaling a service changes client configuration. Cross-cutting policy is repeated at every public address, and observability has no consistent entry point.

A gateway reduces that coupling, but creates a new availability boundary. It must remain stateless, horizontally scalable, fast, and free of business/database work.

## Monolith vs Microservices

In a monolith, one HTTP server already acts as the entry point. A security filter authenticates once, controllers call in-process modules, and no network timeout exists between those modules.

In microservices, routing crosses process and network boundaries. A backend can be slow, unavailable, or called directly. Therefore:

- every remote call needs explicit time bounds;
- identity must remain verifiable across hops;
- protected services still authorize their own resources;
- correlation metadata must propagate across processes;
- retry decisions must account for ambiguous remote outcomes.

## Request Flow

```text
Client
  -> normalize X-Correlation-ID
  -> match route
  -> verify JWT signature / issuer / expiry
  -> check coarse role
  -> remove spoofed identity headers
  -> forward bearer token with bounded timeout
  -> backend verifies token and domain permission again
  -> normalize response correlation header
```

## Why WebFlux Here?

Spring Cloud Gateway's server runtime is reactive. A gateway spends much of its time waiting on network I/O, so a small event-loop model can handle many concurrent connections without dedicating a blocking thread to every request. This does not mean the JPA business services need to become reactive; they remain imperative because JDBC/JPA are blocking.

Mixing the two stacks is a deliberate boundary: reactive edge proxy, imperative transactional services.

## Authentication vs Authorization

Authentication verifies who sent the request. Gateway validates Auth's RS256 signature and issuer. Authorization decides what that identity may do. Gateway answers only coarse questions such as “is this an ADMIN route?” User Service answers the domain question “does this JWT subject own this profile/address?”

This is defense in depth, not redundant business logic.

## Timeout, Retry, and Failure

A connection timeout bounds reaching a downstream host. A response timeout bounds waiting after connection. Neither proves whether a timed-out write committed. Gateway therefore does not automatically retry writes. Later, an idempotency key or operation-specific retry policy may make selected calls safe.

Questions to ask for every route:

1. Does the caller need an immediate answer?
2. What happens if the downstream is unavailable?
3. Can the operation be retried without duplicating state?
4. Which service owns the final authorization decision?
5. What signal lets an operator trace the request across services?

## Common Mistakes

- Putting order/payment business orchestration inside Gateway.
- Trusting `X-User-Id` supplied by the caller.
- Validating JWT at Gateway but leaving backends unprotected.
- Omitting timeouts or using unbounded retries.
- Retrying non-idempotent POST requests after ambiguous failures.
- Logging bearer tokens while debugging routing.
- Using Gateway for internal service-to-service calls, creating unnecessary coupling and a single failure path.
- Assuming a health endpoint proves all downstream routes work.

## Exercise

Design the future `POST /api/v1/orders` edge rule. Decide which roles may call it, whether Gateway should retry it, what must be propagated, and which checks must remain in Order Service. Then explain what the client should receive if Product times out while Order validates an item snapshot.
