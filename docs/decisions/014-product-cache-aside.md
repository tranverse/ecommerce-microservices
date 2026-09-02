# ADR 014: Use Failure-Tolerant Cache-Aside for Product Reads

## Status

Accepted and implemented for Product-by-ID and batch reads.

## Context

Product catalog reads are frequent, while current product state remains authoritative in `product_db`. Order also resolves up to 50 trusted product snapshots in one synchronous request. Repeating every hot read against PostgreSQL wastes capacity, but introducing Redis creates staleness and another network failure mode. A cache must improve latency without becoming a second source of truth or reducing Product availability.

## Options considered

1. No cache: simplest consistency model, but every hot read consumes database work and leaves no practical Redis learning path.
2. Spring cache annotations: concise, but hides batch multi-get/pipelining behavior and makes failure policy and metrics less explicit.
3. Explicit cache-aside port with a Redis adapter: keeps Product logic independent of Redis APIs, exposes failure semantics, and supports efficient batch operations at modest code cost.
4. Write-through cache: can reduce miss latency after writes, but coordinating cache and database commits without a distributed transaction creates rollback and visibility races.
5. Event-fed Product read model: useful at much larger scale, but adds event contracts, consumer lag, repair, and operational complexity not justified by the current system.

## Decision

Choose option 3.

- PostgreSQL is the sole source of truth. Redis stores serialized `ProductResponse` snapshots under namespaced UUID keys with a configurable five-minute TTL.
- Single reads use GET; misses load PostgreSQL and populate Redis best effort.
- Batch reads use one MGET, one `findAllById` for all misses, and pipelined SET commands. Missing products are not negative-cached.
- Product updates evict after the local database transaction commits. Creates do not pre-populate the cache.
- Search results are not cached because arbitrary filters and pages produce high-cardinality keys and broad invalidation requirements.
- Redis connect and command timeouts default to 500 ms. Cache read/write/serialization failures are sanitized, metered, rate-limited in logs, and never fail the business request.
- Redis health is excluded from Product readiness. Database health remains readiness-relevant.
- Cache metric tags contain only finite result or operation names; product IDs are never metric labels.

## Consequences

- Hot ID and batch reads reduce PostgreSQL load without changing API contracts.
- Redis can be restarted or emptied and the cache rebuilds naturally.
- Reads may be stale for at most the configured TTL if eviction fails. This is accepted for catalog display, but would not be acceptable for Inventory quantity or payment state.
- A cache miss during Redis failure adds at most the bounded Redis timeout before the database read; alerts must detect sustained cache errors and falling hit ratio.
- The explicit adapter is more code than annotations, but batch behavior, tests, metrics, and failure handling remain reviewable.
- Multi-instance correctness does not depend on local memory; all instances share the external cache namespace.
