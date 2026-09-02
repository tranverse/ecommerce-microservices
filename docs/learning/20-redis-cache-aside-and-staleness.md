# 20. Redis Cache-Aside and Staleness

## Concept

A cache keeps a disposable copy of data closer to a frequent read path. In **cache-aside**, the application asks the cache first, loads the source of truth on a miss, and then stores that result with a time-to-live (TTL). The cache does not participate in the database transaction.

## Why Microservices Need It

Catalog traffic is read-heavy and Order needs immediate trusted Product snapshots. Redis can reduce PostgreSQL work and latency across multiple Product instances. It also adds a remote dependency: commands can time out, cached values can be corrupt or stale, and invalidation can race with database commits.

In a monolith, an in-process cache can be fast and simple, but every application instance has a different copy and invalidation must reach all of them. In microservices, Product alone owns catalog cache policy and uses a shared external Redis namespace. Other services must not query Product's Redis keys; they consume Product's API or own an explicitly designed read model.

## Current Flow

```text
GET product -> Redis hit --------------------------> response
            -> miss/failure -> PostgreSQL -> response
                                      \-> best-effort SET + TTL

UPDATE product -> PostgreSQL commit -> best-effort DEL cache key
```

PostgreSQL remains authoritative. Redis is not included in readiness because restarting healthy Product instances during a cache outage would amplify the incident and remove the database fallback.

## Consistency and the Transaction Boundary

Evicting before commit is unsafe:

1. Update transaction changes Product but has not committed.
2. Application deletes the old cache entry.
3. A concurrent reader misses Redis and still sees the old committed row.
4. That reader writes the old value back to Redis.
5. The update commits, leaving a stale cache entry.

The implementation registers eviction for `afterCommit`. If the database transaction rolls back, no invalidation is needed. If Redis eviction itself fails, TTL provides a bounded stale window instead of correctness depending on a second commit.

This is eventual consistency: a Product read can briefly lag the source of truth. That trade-off is acceptable for catalog text and display price in this project. Order still requests Product's current snapshot and has no fallback price; Inventory and Payment state are not cached under this policy because their correctness and concurrency requirements differ.

## Avoiding N+1 Work

For 50 IDs, calling Redis GET and PostgreSQL `findById` 50 times would create network and database N+1 patterns. Product instead performs:

1. one Redis MGET for all IDs;
2. one PostgreSQL `findAllById` query for all misses;
3. one pipelined group of Redis SET commands.

Pipelining reduces network round trips; it is not a Redis transaction and atomicity is unnecessary because every cache entry is independently disposable.

## Failure and Observability Policy

- Connect and command timeouts are both 500 ms by default.
- Redis failures become cache misses; PostgreSQL failure still fails the request.
- Invalid JSON or a mismatched product ID is deleted best effort and reloaded.
- Logs expose operation and exception type, not cached payloads, and repeated warnings are rate-limited.
- Hit/miss, write, eviction, and bounded-operation error counters expose cache behavior without high-cardinality product-ID labels.
- Sustained errors, low hit ratio, or rising database latency should alert operators even though readiness stays `UP`.

## Review Questions

1. Why is Redis not a second Product database?
2. What race occurs if eviction happens before the PostgreSQL commit?
3. Why is stale catalog text tolerable while stale inventory quantity may not be?
4. Why does one MGET plus one database query scale better than a loop of single reads?
5. When should Redis failure affect readiness instead of failing open?

## Practical Exercise

Start the Compose stack, read the same product twice, and compare `ecommerce_product_cache_requests_total` hit/miss counters. Stop Redis, read the product again, and verify Product readiness remains `UP` while the authoritative response still comes from PostgreSQL. Restart Redis and observe the cache repopulate without a repair job.
