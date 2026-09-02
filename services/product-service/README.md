# Product Service

Product Service owns the product catalog: identity, immutable SKU, descriptive information, current price, currency, and lifecycle status. Inventory quantity deliberately belongs to Inventory Service.

PostgreSQL is the source of truth. Redis accelerates product-by-ID and batch snapshot reads with a five-minute cache-aside policy; search results remain uncached because their invalidation and key cardinality are substantially more complex.

## API

Base path: `/api/v1/products`

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/products` | Create a catalog product | `201 Created` |
| `GET` | `/api/v1/products/{id}` | Read one product | `200 OK` |
| `GET` | `/api/v1/products` | Search, filter, sort, and paginate | `200 OK` |
| `GET` | `/api/v1/products/batch?ids=...` | Resolve up to 50 product snapshots in one call | `200 OK` |
| `PUT` | `/api/v1/products/{id}` | Replace mutable product details | `200 OK` |

Search parameters are `query`, `status`, `minimumPrice`, `maximumPrice`, `page`, `size`, `sortBy`, and `direction`. Page size is capped at 100. Allowed sort fields are `name`, `sku`, `price`, `status`, and `createdAt`.

The batch read supports Order Service's synchronous snapshot lookup. It deduplicates IDs, caps fan-out at 50, performs one repository query, and returns results in deterministic ID order. Missing IDs are omitted so the caller can apply its own domain error policy.

The SKU cannot be changed after creation. Catalog removal is represented by `INACTIVE`; hard deletion would break historical references and is not exposed.

Update requests carry the last observed `version`. A stale version returns `409 PRODUCT_VERSION_CONFLICT`, preventing silent lost updates. The database-backed JPA version column remains the final concurrency guard if two requests race after the application check.

## Example

```http
POST /api/v1/products
Content-Type: application/json
X-Correlation-ID: demo-product-create

{
  "sku": "LAPTOP-001",
  "name": "Developer Laptop",
  "description": "A workstation for software development",
  "price": 1499.00,
  "currency": "USD",
  "status": "ACTIVE"
}
```

Errors use one response shape:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 404,
  "errorCode": "PRODUCT_NOT_FOUND",
  "message": "Product '...' was not found",
  "details": [],
  "path": "/api/v1/products/...",
  "correlationId": "demo-request"
}
```

Swagger UI is available at `http://localhost:8083/swagger-ui.html` and the OpenAPI document at `http://localhost:8083/v3/api-docs`.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `PRODUCT_DB_PASSWORD` | Yes | None | Product database password |
| `PRODUCT_DB_URL` | No | `jdbc:postgresql://localhost:5432/product_db` | JDBC URL |
| `PRODUCT_DB_USERNAME` | No | `product_app` | Database owner/user |
| `PRODUCT_DB_POOL_SIZE` | No | `10` | Maximum Hikari connections |
| `PRODUCT_DB_MIN_IDLE` | No | `2` | Minimum idle Hikari connections |
| `PRODUCT_REDIS_URL` | No | `redis://localhost:6379` | Redis connection URL |
| `PRODUCT_REDIS_CONNECT_TIMEOUT` | No | `PT0.5S` | Maximum Redis connection establishment time |
| `PRODUCT_REDIS_COMMAND_TIMEOUT` | No | `PT0.5S` | Maximum Redis command time |
| `PRODUCT_CACHE_ENABLED` | No | `true` | Enable Product read caching without changing business behavior |
| `PRODUCT_CACHE_KEY_PREFIX` | No | `ecommerce:product:` | Namespace for Product cache keys |
| `PRODUCT_CACHE_TTL` | No | `PT5M` | Maximum lifetime of a cached product snapshot |
| `SERVER_PORT` | No | `8083` | HTTP port |

No credential is stored in source control. Run `scripts/bootstrap-local-env.ps1` once to generate ignored local secrets before starting Compose.

Redis is an optimization, not an availability dependency. Redis health is excluded from Product readiness; a connection, command, serialization, or value-validation failure is recorded and treated as a cache miss. PostgreSQL failures still fail the request because Product cannot safely fabricate authoritative catalog data.

Cache metrics are exposed through the loopback-published local actuator endpoint. Production must enforce its own network boundary:

- `ecommerce_product_cache_requests_total{result="hit|miss"}`
- `ecommerce_product_cache_errors_total{operation="..."}`
- `ecommerce_product_cache_writes_total`
- `ecommerce_product_cache_evictions_total`

## Build and Test

From the repository root:

```powershell
cmd /c mvnw.cmd -pl services/product-service test
cmd /c mvnw.cmd -pl services/product-service package
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:local .
```

The 28 tests include pure domain/service tests, after-commit cache invalidation, cache failure behavior and metrics, batch lookup behavior, a Spring MVC slice, JPA repository tests, Flyway validation, and a full HTTP cache lifecycle against PostgreSQL 17.6 and Redis 8.2 through Testcontainers.

## Internal Design

- `controller` translates HTTP to application calls; it has no repository access.
- `service` owns local transaction boundaries and business workflow.
- `domain` protects SKU, price, currency, and lifecycle invariants.
- `repository` contains JPA persistence and composable search specifications.
- `cache` defines an application-facing port and its failure-tolerant Redis adapter; Product business code does not depend on Redis APIs.
- `dto` is the public API contract; JPA entities never leave the service.
- `exception` converts expected failures to a stable client contract.
- `web` establishes a correlation ID for responses and logs.
- `db/migration` is the only source of schema changes; Hibernate only validates.

Product currently has no JPA relationships, so there is no N+1 query path. Future relationships must be justified from concrete query patterns rather than made eager by default.

Single reads use cache-aside. Batch reads use one Redis multi-get, one `findAllById` query for all misses, and pipelined cache writes, avoiding both database and network N+1 patterns. Updates evict only after the PostgreSQL transaction commits, closing the race where another request could repopulate stale data before commit. If eviction fails, TTL bounds staleness to five minutes by default.
