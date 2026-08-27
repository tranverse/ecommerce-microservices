# Product Service

Product Service owns the product catalog: identity, immutable SKU, descriptive information, current price, currency, and lifecycle status. Inventory quantity deliberately belongs to Inventory Service.

## API

Base path: `/api/v1/products`

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/api/v1/products` | Create a catalog product | `201 Created` |
| `GET` | `/api/v1/products/{id}` | Read one product | `200 OK` |
| `GET` | `/api/v1/products` | Search, filter, sort, and paginate | `200 OK` |
| `PUT` | `/api/v1/products/{id}` | Replace mutable product details | `200 OK` |

Search parameters are `query`, `status`, `minimumPrice`, `maximumPrice`, `page`, `size`, `sortBy`, and `direction`. Page size is capped at 100. Allowed sort fields are `name`, `sku`, `price`, `status`, and `createdAt`.

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
| `SERVER_PORT` | No | `8083` | HTTP port |

No credential is stored in source control. Copy the root `.env.example` and choose a local-only password when Compose support is introduced.

## Build and Test

From the repository root:

```powershell
cmd /c mvnw.cmd -pl services/product-service test
cmd /c mvnw.cmd -pl services/product-service package
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:local .
```

Tests include pure domain/service tests, a Spring MVC slice, JPA repository tests, Flyway validation, and a full HTTP integration flow against PostgreSQL 17.6 through Testcontainers.

## Internal Design

- `controller` translates HTTP to application calls; it has no repository access.
- `service` owns local transaction boundaries and business workflow.
- `domain` protects SKU, price, currency, and lifecycle invariants.
- `repository` contains JPA persistence and composable search specifications.
- `dto` is the public API contract; JPA entities never leave the service.
- `exception` converts expected failures to a stable client contract.
- `web` establishes a correlation ID for responses and logs.
- `db/migration` is the only source of schema changes; Hibernate only validates.

Product currently has no JPA relationships, so there is no N+1 query path. Future relationships must be justified from concrete query patterns rather than made eager by default.
