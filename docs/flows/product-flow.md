# Product Service Request Flow

## Ownership

Product Service is authoritative for product identity, catalog text, status, current price, and currency. It does not own stock. A caller that needs availability must ask Inventory Service rather than infer it from catalog status.

## Create Product

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as ProductController
    participant App as ProductService
    participant Repo as ProductRepository
    participant DB as product_db

    Client->>API: POST /api/v1/products + X-Correlation-ID
    API->>API: Deserialize and validate DTO
    API->>App: createProduct(request)
    App->>App: Normalize SKU and enforce domain invariants
    App->>Repo: existsBySku(normalizedSku)
    Repo->>DB: SELECT existence
    App->>Repo: saveAndFlush(product)
    Repo->>DB: INSERT inside local transaction
    DB-->>Repo: UUID, version, timestamps
    App-->>API: ProductResponse
    API-->>Client: 201 Created + Location + X-Correlation-ID
```

The pre-insert existence check produces a useful business error in the normal duplicate case. It cannot prevent a race between two requests, so the unique database constraint is authoritative. `saveAndFlush` makes that constraint fail before the application returns success.

## Update Product

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as ProductController
    participant App as ProductService
    participant DB as product_db

    Client->>API: PUT /api/v1/products/{id} with version N
    API->>App: updateProduct(id, request)
    App->>DB: SELECT product
    alt Request version is stale
        App-->>Client: 409 PRODUCT_VERSION_CONFLICT
    else Version matches
        App->>App: Apply validated domain changes
        App->>DB: UPDATE ... WHERE id=? AND version=N
        DB-->>App: version N+1
        App-->>Client: 200 ProductResponse
    end
```

The application check explains the conflict clearly. JPA `@Version` adds the real atomic guard at the database write, covering the race where two requests both read version N before either commits.

## Transaction Boundary

Each create or update method has one local PostgreSQL transaction. A read method is marked read-only. This transaction cannot include another microservice: when Order later obtains a product snapshot over HTTP, Product's read transaction and Order's write transaction remain independent.

## Failure Behavior

| Failure | Result |
| --- | --- |
| Invalid request DTO/query parameter | `400 VALIDATION_ERROR` or `MALFORMED_REQUEST` |
| Unknown product | `404 PRODUCT_NOT_FOUND` |
| Duplicate normalized SKU | `409 PRODUCT_SKU_CONFLICT` |
| Stale update | `409 PRODUCT_VERSION_CONFLICT` |
| Unexpected internal failure | Sanitized `500 INTERNAL_ERROR`; full detail only in server logs |

Every response preserves or creates `X-Correlation-ID`. The same value is placed in the logging MDC so an API error can be matched to service logs.
