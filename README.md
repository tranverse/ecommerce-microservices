# E-Commerce Microservices Platform

A production-style Java 21 and Spring Boot 3 e-commerce system built incrementally as both a runnable distributed application and a practical microservices course.

> **Implementation status:** Auth, Product, and Inventory Services are implemented, containerized, and verified with PostgreSQL/security/concurrency integration tests. User provisioning, Gateway enforcement, and the connected order workflow remain planned milestones.

## Project Overview

The completed platform will support this business journey:

```text
Register -> Login -> Browse products -> Check inventory -> Create order
         -> Reserve inventory -> Process payment -> Confirm or cancel
         -> Send notification
```

The project emphasizes the engineering problems behind that journey: data ownership, concurrency, partial failure, distributed transactions, idempotency, security, resilience, observability, testing, and operability.

## Why Microservices?

The service boundaries have different invariants and operational profiles. Catalog traffic is read-heavy, inventory reservation is concurrency-sensitive, authentication protects credentials, payment has its own idempotency rules, and notification delivery must not block order completion.

Microservices let those capabilities own their data and evolve independently. They also introduce network failure, eventual consistency, contract evolution, and operational cost. This repository makes those costs visible instead of presenting microservices as a collection of CRUD projects.

See [Monolith vs Microservices](docs/learning/01-monolith-vs-microservices.md) for the full comparison.

## Architecture

```mermaid
flowchart LR
    Client[Client] --> Gateway[API Gateway]
    Gateway --> Auth[Auth Service]
    Gateway --> User[User Service]
    Gateway --> Product[Product Service]
    Gateway --> Order[Order Service]
    Gateway --> Inventory[Inventory Admin API]

    Order -->|REST product snapshot| Product
    Order -->|Kafka saga commands| Kafka[(Kafka)]
    Kafka --> Inventory[Inventory Service]
    Kafka --> Payment[Payment Service]
    Kafka --> Order
    Kafka --> Notification[Notification Service]

    Auth --> AuthDB[(auth_db)]
    User --> UserDB[(user_db)]
    Product --> ProductDB[(product_db)]
    Inventory --> InventoryDB[(inventory_db)]
    Order --> OrderDB[(order_db)]
    Payment --> PaymentDB[(payment_db)]
    Notification --> NotificationDB[(notification_db)]
```

The complete design and current-state warning are maintained in [System Overview](docs/architecture/system-overview.md).

## Services

| Service | Responsibility | Status |
| --- | --- | --- |
| API Gateway | Public routing, correlation IDs, edge security | Planned |
| Auth Service | Credentials, password hashing, JWT and roles | Implemented |
| User Service | Customer profiles and addresses | Planned |
| Product Service | Catalog, current prices, filtering and management | Implemented |
| Inventory Service | Stock, reservations, releases and concurrency | Implemented |
| Order Service | Orders, immutable item snapshots and saga orchestration | Planned |
| Payment Service | Idempotent simulated payments and compensation | Planned |
| Notification Service | Asynchronous notification history and delivery simulation | Planned |

Detailed ownership and prohibited coupling are documented in [Service Boundaries](docs/architecture/service-boundaries.md).

## Technology Stack

| Technology | Why it is used | Status |
| --- | --- | --- |
| Java 21 | LTS runtime, records, modern language/runtime features | Active |
| Spring Boot 3.5 | Production application foundation and dependency management | Active |
| Maven Wrapper | Reproducible builds without global Maven installation | Active |
| PostgreSQL | Strong relational constraints and transactional service data | Active in Product and Inventory |
| Flyway | Versioned, reviewable service-owned schema migrations | Active in Product and Inventory |
| Spring Security and JWT | Auth lifecycle and public JWKS | Active in Auth; enforcement pending Gateway/services |
| Spring Cloud Gateway | Reactive edge routing without business logic | Planned |
| Kafka | Durable asynchronous saga communication and notifications | Planned |
| Testcontainers | Integration tests against real PostgreSQL/Kafka behavior | Active for PostgreSQL |
| Resilience4j | Bounded failure handling for justified synchronous calls | Planned |
| Micrometer/OpenTelemetry | Metrics and distributed traces | Planned |
| Docker Compose | Reproducible complete local environment | Planned |

Redis and Kubernetes are deliberately deferred until a concrete need exists and Docker Compose works end to end.

## Repository Structure

Current:

```text
.
├── services/
│   ├── auth-service/           # Credentials, JWT/JWKS, auth_db, security tests
│   ├── product-service/        # Catalog API, product_db, tests, and image
│   └── inventory-service/      # Stock reservations, inventory_db, concurrency tests
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── flows/
│   └── learning/
├── pom.xml                     # Current reactor build aggregator
├── mvnw
├── mvnw.cmd
├── .mvn/
└── README.md
```

Accepted target:

```text
.
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── user-service/
│   ├── product-service/
│   ├── inventory-service/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
├── contracts/                  # Versioned HTTP/event schemas, not shared entities
├── infrastructure/
│   ├── database/
│   ├── kafka/
│   └── monitoring/
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── flows/
│   └── learning/
├── pom.xml                     # Build aggregation only
├── compose.yaml
└── .env.example
```

The rationale is recorded in [ADR 001](docs/decisions/001-monorepo.md).

## Database Architecture

Every persistent service exclusively owns one logical database and credentials. No service may query another service's tables or share JPA entities. Local development may co-locate logical databases on one PostgreSQL server to save resources; ownership remains isolated.

See [Database Architecture](docs/architecture/database-architecture.md) and [ADR 002](docs/decisions/002-database-per-service.md).

## Authentication

Planned flow:

1. Auth Service registers credentials and hashes passwords.
2. Login returns a short-lived JWT access token with subject and roles.
3. Gateway validates the token for edge routing decisions.
4. Backend resource services validate the token and enforce their own authorization.
5. User Service owns profile data; it never stores passwords.

Signing secrets or private keys will come from environment/runtime secret management and will never be committed.

## Communication

Synchronous HTTP is reserved for immediate answers. The primary example is Order requesting trusted product status and price snapshots before accepting an order. Kafka handles durable saga steps and notifications where eventual consistency is acceptable.

Internal service calls bypass Gateway. Every network dependency must define timeouts, availability behavior, retry safety, and correlation-ID propagation. See [Communication](docs/architecture/communication.md).

## Kafka

Kafka will carry versioned commands and events such as:

```text
InventoryReservationRequested
InventoryReserved | InventoryReservationFailed
PaymentRequested
PaymentCompleted | PaymentFailed
InventoryReleaseRequested
OrderConfirmed | OrderCancelled
NotificationRequested
```

Messages will include event identity, version, timestamp, aggregate ID, and correlation ID. Producers that update state and publish will use a transactional outbox. Consumers will assume at-least-once delivery and enforce idempotency.

## Order Workflow

The order endpoint will validate products synchronously, save a `PENDING` order with immutable item snapshots, and return `202 Accepted`. Inventory and payment then advance the order asynchronously. Clients query the order resource to observe the final outcome.

## Saga

Order Service will orchestrate the saga because it owns the customer-visible lifecycle and state machine. Inventory failure cancels the order. Payment failure after reservation requests an inventory release and then cancels the order. Notification reacts only after a durable business outcome.

The decision and trade-offs are in [ADR 003](docs/decisions/003-orchestrated-order-saga.md). Detailed flow documentation will be added with the implementation.

## Reliability

The target design uses:

- Explicit connection and response timeouts
- Bounded retries only for transient idempotent operations
- Circuit breakers for justified synchronous dependencies
- Transactional outbox for state-plus-message consistency
- Processed-event uniqueness for consumer idempotency
- Validated order state transitions
- Compensation rather than cross-service rollback

These mechanisms will not be added before their failure scenario exists in code.

## Observability

Every inbound request will receive or preserve a correlation ID. It will propagate through HTTP, Kafka metadata, and logs. Actuator health, Micrometer metrics, and OpenTelemetry-compatible tracing will be introduced after the connected workflow exists so the signals describe real behavior.

Sensitive values such as passwords, raw tokens, private keys, and payment details must never be logged.

## Running Locally

Requirements:

- Java 21
- Docker Desktop (for PostgreSQL/Testcontainers and image builds)

### Auth Service

Start PostgreSQL, then run Auth with an ephemeral local RSA key:

```powershell
$env:AUTH_DB_PASSWORD = "<choose-a-local-password>"
docker run --name ecommerce-auth-db --rm -d `
  -e POSTGRES_DB=auth_db `
  -e POSTGRES_USER=auth_app `
  -e POSTGRES_PASSWORD=$env:AUTH_DB_PASSWORD `
  -p 5434:5432 postgres:17.6-alpine

$env:AUTH_DB_URL = "jdbc:postgresql://localhost:5434/auth_db"
.\mvnw.cmd -pl services/auth-service spring-boot:run
```

Auth readiness is `http://localhost:8081/actuator/health/readiness`; JWKS is `http://localhost:8081/.well-known/jwks.json`.

Build the production-style local image:

```powershell
docker build -f services/auth-service/Dockerfile -t ecommerce/auth-service:local .
```

### Product Service

Run tests on Windows:

```powershell
.\mvnw.cmd -pl services/product-service test
```

The tests start disposable PostgreSQL 17.6 instances automatically. To run the service manually, start its database and provide a local password:

```powershell
$env:PRODUCT_DB_PASSWORD = "<choose-a-local-password>"
docker run --name ecommerce-product-db --rm -d `
  -e POSTGRES_DB=product_db `
  -e POSTGRES_USER=product_app `
  -e POSTGRES_PASSWORD=$env:PRODUCT_DB_PASSWORD `
  -p 5432:5432 postgres:17.6-alpine
```

Then run the service:

```powershell
.\mvnw.cmd -pl services/product-service spring-boot:run
```

Verify health:

```powershell
Invoke-RestMethod http://localhost:8083/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

Swagger UI: `http://localhost:8083/swagger-ui.html`.

Build the production-style local image:

```powershell
docker build -f services/product-service/Dockerfile -t ecommerce/product-service:local .
```

### Inventory Service

Start a separate PostgreSQL container on host port `5433`, then run the service with the matching JDBC URL:

```powershell
$env:INVENTORY_DB_PASSWORD = "<choose-a-local-password>"
$env:INVENTORY_DB_URL = "jdbc:postgresql://localhost:5433/inventory_db"
docker run --name ecommerce-inventory-db --rm -d `
  -e POSTGRES_DB=inventory_db `
  -e POSTGRES_USER=inventory_app `
  -e POSTGRES_PASSWORD=$env:INVENTORY_DB_PASSWORD `
  -p 5433:5432 postgres:17.6-alpine

.\mvnw.cmd -pl services/inventory-service spring-boot:run
```

Inventory readiness is `http://localhost:8084/actuator/health/readiness`; Swagger UI is `http://localhost:8084/swagger-ui.html`.

```powershell
docker build -f services/inventory-service/Dockerfile -t ecommerce/inventory-service:local .
```

The final target command will be:

```text
docker compose up --build
```

It is not documented as working until end-to-end validation passes.

## Configuration

Product Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Product Service HTTP port | `8083` |
| `PRODUCT_DB_URL` | Product PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/product_db` |
| `PRODUCT_DB_USERNAME` | Product database user | `product_app` |
| `PRODUCT_DB_PASSWORD` | Product database password | Required; no default |
| `PRODUCT_DB_POOL_SIZE` | Maximum database pool size | `10` |
| `PRODUCT_DB_MIN_IDLE` | Minimum idle connections | `2` |

Inventory Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Inventory Service HTTP port | `8084` |
| `INVENTORY_DB_URL` | Inventory PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/inventory_db` |
| `INVENTORY_DB_USERNAME` | Inventory database user | `inventory_app` |
| `INVENTORY_DB_PASSWORD` | Inventory database password | Required; no default |
| `INVENTORY_DB_POOL_SIZE` | Maximum inventory database pool size | `10` |
| `INVENTORY_DB_MIN_IDLE` | Minimum inventory idle connections | `2` |

Auth Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Auth Service HTTP port | `8081` |
| `AUTH_DB_URL` | Auth PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/auth_db` |
| `AUTH_DB_USERNAME` | Auth database user | `auth_app` |
| `AUTH_DB_PASSWORD` | Auth database password | Required; no default |
| `AUTH_JWT_ISSUER` | Exact trusted token issuer | `http://localhost:8081` |
| `AUTH_JWT_ACCESS_TOKEN_TTL` | Access-token lifetime | `PT15M` |
| `AUTH_JWT_REFRESH_TOKEN_TTL` | Refresh-token lifetime | `P30D` |
| `AUTH_JWT_PRIVATE_KEY_BASE64` | Base64 PKCS#8 RSA private key | Ephemeral outside production; required in production |
| `AUTH_JWT_PUBLIC_KEY_BASE64` | Base64 X.509 RSA public key | Derived outside production; required in production |

Kafka and observability variables will be added to `.env.example` with their implementations. A real `.env` file is ignored and never committed.

## API Examples

Register credentials:

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"correct horse battery staple"}'
```

Auth details and refresh/key behavior are documented in [Auth Service](services/auth-service/README.md).

Create a product:

```bash
curl -i -X POST http://localhost:8083/api/v1/products \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: readme-demo" \
  -d '{"sku":"LAPTOP-001","name":"Developer Laptop","description":"Development workstation","price":1499.00,"currency":"USD","status":"ACTIVE"}'
```

Search the catalog:

```bash
curl "http://localhost:8083/api/v1/products?query=laptop&status=ACTIVE&page=0&size=20&sortBy=price&direction=ASC"
```

Product details and the stable error contract are documented in [Product Service](services/product-service/README.md). Order examples will be added only when those endpoints are executable.

Create stock using a Product Service UUID, then reserve it for an Order UUID:

```bash
curl -X PUT http://localhost:8084/api/v1/inventory/items/PRODUCT_UUID \
  -H "Content-Type: application/json" -d '{"totalQuantity":10}'

curl -X POST http://localhost:8084/api/v1/inventory/reservations \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER_UUID","items":[{"productId":"PRODUCT_UUID","quantity":2}]}'
```

Inventory contracts and state semantics are documented in [Inventory Service](services/inventory-service/README.md).

## Testing

Run every implemented service suite:

```powershell
.\mvnw.cmd test
```

The Product suite has 18 tests, Inventory has 17 including a real concurrent reservation race, and Auth has 15 covering cryptography and token lifecycle. Each service uses the smallest meaningful combination of unit, controller, repository, integration, security, and Testcontainers tests.

## Documentation

- [System overview](docs/architecture/system-overview.md)
- [Service boundaries](docs/architecture/service-boundaries.md)
- [Database architecture](docs/architecture/database-architecture.md)
- [Communication policy](docs/architecture/communication.md)
- [Security architecture](docs/architecture/security.md)
- [Authentication flow](docs/flows/authentication-flow.md)
- [Product request flow](docs/flows/product-flow.md)
- [Inventory reservation flow](docs/flows/inventory-flow.md)
- [Architecture decisions](docs/decisions/)
- [Learning notes](docs/learning/)

Documentation is changed in the same milestone as the behavior it describes.

## Learning Guide

Start with:

1. [Monolith vs Microservices](docs/learning/01-monolith-vs-microservices.md)
2. [Service Boundaries](docs/learning/02-service-boundaries.md)
3. [Database per Service](docs/learning/03-database-per-service.md)
4. [JPA, Flyway, and Local Transactions](docs/learning/04-jpa-flyway-and-local-transactions.md)
5. [Concurrency and Inventory Reservations](docs/learning/05-concurrency-and-inventory-reservations.md)
6. [Authentication in Microservices](docs/learning/06-authentication-in-microservices.md)
7. Read the ADRs and compare their alternatives.
8. Follow the Auth, Product, and Inventory READMEs from controller to service, domain, repository, migration, and tests.

Later notes will reference the exact service, class, endpoint, migration, event, and configuration that implements each concept.

## Engineering Workflow

- `main` remains stable.
- Significant work uses focused feature branches when isolation is useful.
- Logical milestones use Conventional Commits.
- Relevant builds/tests, diff review, secret scan, and self-review are required before push.
- No force pushes or rewritten published history.
- Simplicity and justified decisions matter more than the number of technologies.
