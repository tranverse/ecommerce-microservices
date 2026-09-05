# E-Commerce Microservices Platform

A production-style Java 21 and Spring Boot 3 e-commerce system built incrementally as both a runnable distributed application and a practical microservices course.

> **Implementation status:** API Gateway, Auth, User, Product, Inventory, Order, Payment, and Notification are implemented, containerized, and wired into one Docker Compose environment. The Kafka saga confirms orders and inventory after payment success, or releases inventory and cancels orders after payment failure. Notification consumes the terminal Order fact idempotently without blocking that workflow. Product uses failure-tolerant Redis cache-aside while PostgreSQL remains authoritative. Metrics, distributed tracing, structured centralized logs, and a provisioned Grafana dashboard cover the connected runtime. Compose bootstraps ignored local secrets and a stable RSA signing key so Auth restarts do not invalidate existing access tokens. GitHub Actions now runs the full reactor and builds every service image; version tags publish immutable, attestable images to GHCR. Automatic profile provisioning remains a planned milestone.

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
    Inventory -->|Kafka outcomes| Kafka
    Kafka --> Payment[Payment Service]
    Kafka --> Order
    Kafka --> Notification[Notification Service]

    Auth --> AuthDB[(auth_db)]
    User --> UserDB[(user_db)]
    Product --> ProductDB[(product_db)]
    Product -.->|cache-aside| Redis[(Redis)]
    Inventory --> InventoryDB[(inventory_db)]
    Order --> OrderDB[(order_db)]
    Payment --> PaymentDB[(payment_db)]
    Notification --> NotificationDB[(notification_db)]
```

The complete design and current-state warning are maintained in [System Overview](docs/architecture/system-overview.md).

## Services

| Service | Responsibility | Status |
| --- | --- | --- |
| API Gateway | Public routing, correlation IDs, edge security | Implemented |
| Auth Service | Credentials, password hashing, JWT and roles | Implemented |
| User Service | Customer profiles and addresses | Implemented |
| Product Service | Catalog, current prices, filtering, management, and failure-tolerant read caching | Implemented |
| Inventory Service | Stock, reservations, releases and concurrency | REST API and Kafka saga participant implemented |
| Order Service | Authenticated orders, immutable item snapshots and saga orchestration | Acceptance, Inventory/Payment outcomes, confirmation, and compensation implemented |
| Payment Service | Idempotent simulated charges, declines, outage recovery and refunds | Core workflow and Kafka saga participant implemented |
| Notification Service | Asynchronous notification history and delivery simulation | Terminal Order consumer implemented |

Detailed ownership and prohibited coupling are documented in [Service Boundaries](docs/architecture/service-boundaries.md).

## Technology Stack

| Technology | Why it is used | Status |
| --- | --- | --- |
| Java 21 | LTS runtime, records, modern language/runtime features | Active |
| Spring Boot 3.5 | Production application foundation and dependency management | Active |
| Maven Wrapper | Reproducible builds without global Maven installation | Active |
| PostgreSQL | Strong relational constraints and transactional service data | Active in every persistent service, including Notification |
| Flyway | Versioned, reviewable service-owned schema migrations | Active in every persistent service, including Notification |
| Spring Security and JWT | Auth lifecycle, public JWKS, local token validation | Active in Auth, User, Order, and Gateway; other services pending |
| Spring Cloud Gateway | Reactive edge routing without business logic | Active |
| Kafka | Durable asynchronous saga communication and notifications | Active through terminal Order outcomes and Notification delivery |
| Testcontainers | Integration tests against real dependency behavior | Active for PostgreSQL, Kafka, and Redis |
| Resilience4j | Bounded failure handling for justified synchronous calls | Active for Order-to-Product |
| Redis | Shared disposable cache for Product ID and batch reads | Active with fail-open behavior and bounded TTL |
| Micrometer/OpenTelemetry | Prometheus metrics and OTLP distributed traces | Active across all applications |
| Grafana, Tempo, Loki, Alloy | Dashboards, trace storage, centralized logs and collection | Active in local Compose |
| Docker Compose | Reproducible complete local environment | Active with E2E verification |
| GitHub Actions and GHCR | Automated quality gates and immutable container artifacts | Active for branches, pull requests, and version tags |

Kubernetes is deliberately deferred until its deployment milestone; the complete Docker Compose environment remains the executable local baseline.

## Repository Structure

Current:

```text
.
├── .github/
│   ├── workflows/              # Reactor verification, image builds, and versioned releases
│   └── dependabot.yml          # Scheduled Maven and Actions update pull requests
├── contracts/                  # Versioned event schemas shared as contracts, never entities
├── services/
│   ├── api-gateway/            # Edge routing, JWT roles, timeouts, correlation IDs
│   ├── auth-service/           # Credentials, JWT/JWKS, auth_db, security tests
│   ├── user-service/           # Profiles, addresses, user_db, ownership tests
│   ├── product-service/        # Catalog API, product_db, Redis cache adapter, tests, and image
│   ├── inventory-service/      # Stock reservations, inventory_db, concurrency tests
│   ├── order-service/          # Authenticated acceptance, snapshots, order_db, state machine
│   ├── payment-service/        # Idempotent charge/refund workflow and payment_db
│   └── notification-service/   # Terminal Order consumer, delivery history, notification_db
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── flows/
│   └── learning/
├── infrastructure/             # Local databases and observability configuration
├── scripts/                    # Secret bootstrap and repeatable verification tools
├── pom.xml                     # Current reactor build aggregator
├── mvnw
├── mvnw.cmd
├── .mvn/
├── compose.yaml
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
│   └── postgres/               # Local role/database bootstrap only
├── docs/
│   ├── architecture/
│   ├── decisions/
│   ├── flows/
│   └── learning/
├── scripts/                    # Secret bootstrap and repeatable local verification tools
├── pom.xml                     # Build aggregation only
├── compose.yaml
└── .env.example
```

The rationale is recorded in [ADR 001](docs/decisions/001-monorepo.md).

## Database Architecture

Every persistent service exclusively owns one logical database and credentials. No service may query another service's tables or share JPA entities. Local development may co-locate logical databases on one PostgreSQL server to save resources; ownership remains isolated.

See [Database Architecture](docs/architecture/database-architecture.md) and [ADR 002](docs/decisions/002-database-per-service.md).

## Authentication

Current and target flow:

1. Auth Service registers credentials, hashes passwords, and returns short-lived RS256 JWTs. Implemented.
2. User Service validates JWTs through public JWKS and derives profile ownership from `sub`. Implemented.
3. Gateway validates the token for coarse edge routing decisions. Implemented.
4. Order Service independently validates tokens and scopes orders to JWT `sub`. Implemented.
5. Other protected backend services will add their own enforcement in their milestones. Planned.
6. User Service owns profile data and never stores passwords or credential email.

Signing secrets or private keys will come from environment/runtime secret management and will never be committed.

## Communication

Synchronous HTTP is reserved for immediate answers. The primary example is Order requesting trusted product status and price snapshots before accepting an order. Kafka handles durable saga steps and notifications where eventual consistency is acceptable.

Internal service calls bypass Gateway. Every network dependency must define timeouts, availability behavior, retry safety, and correlation-ID propagation. See [Communication](docs/architecture/communication.md).

## Kafka

Kafka carries versioned commands and events such as:

```text
InventoryReservationRequested
InventoryReserved | InventoryReservationFailed
PaymentRequested
PaymentCompleted | PaymentFailed
InventoryReleaseRequested
OrderConfirmed | OrderCancelled
```

Messages include event identity, version, timestamp, aggregate ID, and correlation ID. Order, Inventory, and Payment producers persist messages in transactional outboxes. Consumers assume at-least-once delivery, validate the v1 contract, record processed event IDs in a local inbox, and send poison messages to a DLT. Technical failures receive bounded exponential retry; invalid or state-conflicting messages do not. DLT publication must be broker-acknowledged, failure/recovery metrics are exported, and replay targets one explicit topic/partition/offset. Notification reacts directly to terminal Order facts, so an extra `NotificationRequested` command is unnecessary.

## Order Workflow

The order endpoint validates products synchronously through one bounded batch call, saves a `PENDING` order with immutable item snapshots and an `InventoryReservationRequested` outbox row, and returns `202 Accepted`. The publisher sends that command to Kafka only after the local commit. Customer-scoped idempotency makes ambiguous client retries safe. Order consumes Inventory outcomes idempotently: success moves the order to `PAYMENT_PENDING` and writes `PaymentRequested`; failure cancels the order. It then consumes Payment outcomes: success confirms the order and inventory, while decline requests inventory release and cancels the order. See [Order Service](services/order-service/README.md) and [Order Creation Flow](docs/flows/order-creation-flow.md).

## Saga

Order Service orchestrates the saga because it owns the customer-visible lifecycle and state machine. The Inventory round trip, Payment participant, terminal confirmation, and payment-failure compensation are implemented. Payment success atomically creates `InventoryConfirmationRequested` and `OrderConfirmed`; payment failure atomically creates `InventoryReleaseRequested` and `OrderCancelled`. Notification now reacts only after one of those durable business outcomes.

The decision and trade-offs are in [ADR 003](docs/decisions/003-orchestrated-order-saga.md) and [Saga Compensation and Eventual Consistency](docs/learning/12-saga-compensation-and-eventual-consistency.md).

## Payment Workflow

Payment Service persists one payment per opaque Order ID and implements `PENDING -> COMPLETED`, `PENDING -> FAILED`, and `COMPLETED -> REFUNDED` rules. It now consumes strict `PaymentRequested` v1 commands and publishes terminal outcomes through its transactional outbox. Provider calls run outside database transactions and use the durable Payment ID as an idempotency key, making ambiguous retries safe. Business declines are terminal; technical outages preserve a retryable state and leave the Kafka input unprocessed. See [Payment Service](services/payment-service/README.md) and [Payment Processing Flow](docs/flows/payment-processing-flow.md).

## Notification Workflow

Notification Service consumes strict `OrderConfirmed` and `OrderCancelled` v1 events in its own consumer group. It atomically stores a `PENDING` notification with its inbox record, invokes the simulated provider outside the database transaction, then records `SENT` or `FAILED`. Exact and semantic duplicates do not create repeat delivery. See [Notification Service](services/notification-service/README.md), [Notification Delivery Flow](docs/flows/notification-flow.md), and [Asynchronous Notifications](docs/learning/13-asynchronous-notifications-and-delivery-semantics.md).

## Reliability

The implemented design uses:

- Explicit connection and response timeouts
- Bounded retries only for transient idempotent operations
- Circuit breakers for justified synchronous dependencies
- Transactional outbox for state-plus-message consistency
- Processed-event uniqueness for consumer idempotency
- Validated order state transitions
- Compensation rather than cross-service rollback
- Acknowledged dead-letter publication, bounded consumer retry, and explicit idempotent replay

Each mechanism is paired with a concrete failure scenario and test; additional resilience is added only when another dependency justifies it.

## Observability

Every inbound request receives or preserves a safe correlation ID, which propagates through HTTP, Kafka envelopes, and structured JSON logs. All eight applications expose Micrometer metrics to Prometheus and export sampled OpenTelemetry spans to Tempo. Grafana Alloy collects explicitly labelled application stdout into Loki. Grafana provisions all three data sources and the **E-Commerce Service Overview** dashboard.

Trace IDs correlate synchronous request segments and link Tempo to Loki. Correlation IDs remain stable across the longer outbox/Kafka saga. Neither is used as a metric or stream label, preventing high-cardinality growth. Sensitive values such as passwords, raw tokens, private keys, and payment details must never be logged. See [Observability Architecture](docs/architecture/observability.md) and [Observability Request Flow](docs/flows/observability-flow.md).

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

### User Service

Keep Auth running, then start User's separate PostgreSQL database and the service:

```powershell
$env:USER_DB_PASSWORD = "<choose-a-local-password>"
docker run --name ecommerce-user-db --rm -d `
  -e POSTGRES_DB=user_db `
  -e POSTGRES_USER=user_app `
  -e POSTGRES_PASSWORD=$env:USER_DB_PASSWORD `
  -p 5435:5432 postgres:17.6-alpine

$env:USER_DB_URL = "jdbc:postgresql://localhost:5435/user_db"
.\mvnw.cmd -pl services/user-service spring-boot:run
```

User readiness is `http://localhost:8082/actuator/health/readiness`. Its protected API validates Auth JWKS at `http://localhost:8081/.well-known/jwks.json` by default.

```powershell
docker build -f services/user-service/Dockerfile -t ecommerce/user-service:local .
```

### API Gateway

Keep the downstream services running, then start the public entry point:

```powershell
$env:GATEWAY_AUTH_ISSUER = "http://localhost:8081"
$env:GATEWAY_AUTH_JWKS_URI = "http://localhost:8081/.well-known/jwks.json"
.\mvnw.cmd -pl services/api-gateway spring-boot:run
```

Gateway health is `http://localhost:8080/actuator/health/readiness`. Client requests should use port `8080`, for example `http://localhost:8080/api/v1/auth/register` and `http://localhost:8080/api/v1/users/me`.

```powershell
docker build -f services/api-gateway/Dockerfile -t ecommerce/api-gateway:local .
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

### Order Service

Keep Auth and Product running, start a separate PostgreSQL database, then run Order:

```powershell
$env:ORDER_DB_PASSWORD = "<choose-a-local-password>"
$env:ORDER_DB_URL = "jdbc:postgresql://localhost:5436/order_db"
docker run --name ecommerce-order-db --rm -d `
  -e POSTGRES_DB=order_db `
  -e POSTGRES_USER=order_app `
  -e POSTGRES_PASSWORD=$env:ORDER_DB_PASSWORD `
  -p 5436:5432 postgres:17.6-alpine

.\mvnw.cmd -pl services/order-service spring-boot:run
```

Order readiness is `http://localhost:8085/actuator/health/readiness`; Swagger UI is `http://localhost:8085/swagger-ui.html`.

```powershell
docker build -f services/order-service/Dockerfile -t ecommerce/order-service:local .
```

### Payment Service

Start its separate PostgreSQL database, then run Payment:

```powershell
$env:PAYMENT_DB_PASSWORD = "<choose-a-local-password>"
$env:PAYMENT_DB_URL = "jdbc:postgresql://localhost:5437/payment_db"
docker run --name ecommerce-payment-db --rm -d `
  -e POSTGRES_DB=payment_db `
  -e POSTGRES_USER=payment_app `
  -e POSTGRES_PASSWORD=$env:PAYMENT_DB_PASSWORD `
  -p 5437:5432 postgres:17.6-alpine

.\mvnw.cmd -pl services/payment-service spring-boot:run
```

Payment readiness is `http://localhost:8086/actuator/health/readiness`. There is no public business API until the Kafka adapter is implemented.

```powershell
docker build -f services/payment-service/Dockerfile -t ecommerce/payment-service:local .
```

### Notification Service

Start its separate PostgreSQL database and Kafka, then run Notification:

```powershell
$env:NOTIFICATION_DB_PASSWORD = "<choose-a-local-password>"
$env:NOTIFICATION_DB_URL = "jdbc:postgresql://localhost:5438/notification_db"
docker run --name ecommerce-notification-db --rm -d `
  -e POSTGRES_DB=notification_db `
  -e POSTGRES_USER=notification_app `
  -e POSTGRES_PASSWORD=$env:NOTIFICATION_DB_PASSWORD `
  -p 5438:5432 postgres:17.6-alpine

.\mvnw.cmd -pl services/notification-service spring-boot:run
```

Notification readiness is `http://localhost:8087/actuator/health/readiness`. There is intentionally no public business API.

```powershell
docker build -f services/notification-service/Dockerfile -t ecommerce/notification-service:local .
```

## Full local stack with Docker Compose

Compose starts eight applications, one Kafka KRaft broker, one PostgreSQL server containing seven service-owned databases/users, one ephemeral Redis cache, and the Prometheus/Tempo/Loki/Alloy/Grafana observability stack. Co-location saves local memory; it does not permit shared tables or credentials.

```powershell
.\scripts\bootstrap-local-env.ps1
docker compose up --build -d
docker compose ps
./scripts/verify-compose.ps1
./scripts/verify-observability.ps1
```

Use the Gateway at `http://localhost:8080`. Ports `8081` through `8087`, PostgreSQL `5432`, Redis `6379`, and Kafka `9092` are bound to `127.0.0.1` for local inspection only. Containers call each other through Compose DNS such as `auth-service:8081`, `product-service:8083`, `postgres:5432`, `redis:6379`, and `kafka:19092`; `localhost` inside a container refers only to that container.

Kafka automatic topic creation is disabled. The one-shot `kafka-init` service creates the five versioned workflow topics and their DLTs before consumers start. Application readiness checks gate dependent startup, but they do not replace runtime timeout/retry/circuit-breaker behavior.

Inspect one DLT record without exposing its payload or changing Kafka state:

```powershell
.\scripts\replay-dlt-record.ps1 `
  -OriginalTopic payment.commands.v1 `
  -Partition 0 `
  -Offset 42
```

After fixing the cause, rerun with `-Execute` to publish exactly that key/value back to the original topic. The DLT record is retained for audit. See [Kafka Consumer Failure Flow](docs/flows/kafka-failure-flow.md) and [ADR 013](docs/decisions/013-kafka-consumer-reliability.md).

Inspect or stop the environment with:

```powershell
docker compose logs -f order-service inventory-service payment-service notification-service
docker compose down
```

The smoke script seeds a unique product and stock item, registers a customer through Gateway, submits an order, and verifies `Order=CONFIRMED`, `Inventory=CONFIRMED`, `Payment=COMPLETED`, and `Notification=SENT`. Its direct Product/Inventory setup calls and database assertions are local test-harness behavior, not production access patterns. The observability script waits for and verifies Prometheus, Tempo, Loki, Alloy, Grafana, all eight application scrape targets, and the expected Compose processes.

Grafana is available at `http://localhost:3000`, Prometheus at `http://localhost:9090`, Tempo at `http://localhost:3200`, Loki at `http://localhost:3100`, and Alloy at `http://localhost:12345`. All ports are loopback-only. The bootstrap script creates cryptographically random local passwords and an RSA signing pair in ignored `.env`; it never prints secret values. Re-running it requires explicit `-Force` because replacing the signing key deliberately invalidates outstanding access tokens.

Named PostgreSQL and Kafka volumes survive `down`. Redis persistence is disabled because the cache is disposable and rebuilds from `product_db`. `docker compose down -v` is an intentional destructive reset. See [Docker Compose and Networking](docs/learning/14-docker-compose-and-networking.md), [Redis Cache-Aside](docs/learning/20-redis-cache-aside-and-staleness.md), and [ADR 014](docs/decisions/014-product-cache-aside.md).

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
| `PRODUCT_REDIS_URL` | Redis connection URL | `redis://localhost:6379` |
| `PRODUCT_REDIS_CONNECT_TIMEOUT` | Redis connection timeout | `PT0.5S` |
| `PRODUCT_REDIS_COMMAND_TIMEOUT` | Redis command timeout | `PT0.5S` |
| `PRODUCT_CACHE_ENABLED` | Enable Product cache-aside | `true` |
| `PRODUCT_CACHE_KEY_PREFIX` | Redis key namespace | `ecommerce:product:` |
| `PRODUCT_CACHE_TTL` | Maximum cache lifetime | `PT5M` |

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
| `AUTH_JWT_KEY_ID` | JWKS identifier for the active signing key | Generated fingerprint in local Compose |
| `AUTH_JWT_PRIVATE_KEY_BASE64` | Base64 PKCS#8 RSA private key | Generated into ignored `.env`; externally supplied in production |
| `AUTH_JWT_PUBLIC_KEY_BASE64` | Matching Base64 X.509 RSA public key | Generated into ignored `.env`; externally supplied in production |
| `AUTH_JWT_REQUIRE_CONFIGURED_KEY` | Fail startup instead of generating an ephemeral key | `true` in Compose and production |

User Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | User Service HTTP port | `8082` |
| `USER_DB_URL` | User PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/user_db` |
| `USER_DB_USERNAME` | User database user | `user_app` |
| `USER_DB_PASSWORD` | User database password | Required; no default |
| `USER_AUTH_ISSUER` | Exact trusted JWT issuer | `http://localhost:8081` |
| `USER_AUTH_JWKS_URI` | Auth public-key endpoint | `http://localhost:8081/.well-known/jwks.json` |
| `USER_AUTH_CONNECT_TIMEOUT` | JWKS connection timeout | `PT2S` |
| `USER_AUTH_READ_TIMEOUT` | JWKS response timeout | `PT2S` |

API Gateway supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Gateway HTTP port | `8080` |
| `GATEWAY_AUTH_ISSUER` | Exact trusted JWT issuer | `http://localhost:8081` |
| `GATEWAY_AUTH_JWKS_URI` | Auth public-key endpoint | `http://localhost:8081/.well-known/jwks.json` |
| `GATEWAY_JWKS_CONNECT_TIMEOUT` | JWKS connection timeout | `PT2S` |
| `GATEWAY_JWKS_READ_TIMEOUT` | JWKS response timeout | `PT2S` |
| `GATEWAY_CONNECT_TIMEOUT_MS` | Downstream connection timeout in milliseconds | `2000` |
| `GATEWAY_RESPONSE_TIMEOUT` | Downstream response timeout | `5s` |
| `*_SERVICE_URL` | Auth/User/Product/Inventory/Order route destinations | Service-specific localhost URL |

Order Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Order Service HTTP port | `8085` |
| `ORDER_DB_URL` | Order PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/order_db` |
| `ORDER_DB_USERNAME` | Order database user | `order_app` |
| `ORDER_DB_PASSWORD` | Order database password | Required; no default |
| `ORDER_AUTH_ISSUER` | Exact trusted JWT issuer | `http://localhost:8081` |
| `ORDER_AUTH_JWKS_URI` | Auth public-key endpoint | `http://localhost:8081/.well-known/jwks.json` |
| `ORDER_PRODUCT_SERVICE_URL` | Direct internal Product endpoint | `http://localhost:8083` |
| `ORDER_PRODUCT_CONNECT_TIMEOUT` | Product connection timeout | `PT0.5S` |
| `ORDER_PRODUCT_READ_TIMEOUT` | Product response timeout | `PT1.5S` |
| `ORDER_PRODUCT_RETRY_MAX_ATTEMPTS` | Total Product GET attempts, capped at 3 | `2` |
| `ORDER_PRODUCT_RETRY_WAIT_DURATION` | Delay before the one transient retry | `PT0.1S` |
| `ORDER_PRODUCT_CB_FAILURE_RATE_THRESHOLD` | Failed logical-call percentage that opens the circuit | `50` |
| `ORDER_PRODUCT_CB_SLIDING_WINDOW_SIZE` | Logical calls retained by the breaker | `10` |
| `ORDER_PRODUCT_CB_MINIMUM_CALLS` | Calls required before failure-rate evaluation | `5` |
| `ORDER_PRODUCT_CB_HALF_OPEN_CALLS` | Recovery probes allowed while half-open | `2` |
| `ORDER_PRODUCT_CB_OPEN_WAIT_DURATION` | Open interval before recovery probes | `PT10S` |

Payment Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Payment Service actuator port | `8086` |
| `PAYMENT_DB_URL` | Payment PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/payment_db` |
| `PAYMENT_DB_USERNAME` | Payment database user | `payment_app` |
| `PAYMENT_DB_PASSWORD` | Payment database password | Required; no default |
| `PAYMENT_DB_POOL_SIZE` | Maximum payment database pool size | `10` |
| `PAYMENT_DB_MIN_IDLE` | Minimum payment idle connections | `2` |
| `PAYMENT_SIMULATOR_DECLINE_PAYMENTS` | Return deterministic business declines | `false` |
| `PAYMENT_SIMULATOR_UNAVAILABLE` | Simulate processor unavailability | `false` |

Notification Service supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | Notification actuator port | `8087` |
| `NOTIFICATION_DB_URL` | Notification PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/notification_db` |
| `NOTIFICATION_DB_USERNAME` | Notification database user | `notification_app` |
| `NOTIFICATION_DB_PASSWORD` | Notification database password | Required; no default |
| `NOTIFICATION_KAFKA_CONSUMER_GROUP` | Independent Kafka consumer group | `notification-service-v1` |
| `ORDER_EVENTS_TOPIC` | Terminal Order event topic | `order.events.v1` |
| `NOTIFICATION_SIMULATOR_FAIL_DELIVERIES` | Persist simulated provider failures | `false` |

Messaging services share `KAFKA_BOOTSTRAP_SERVERS`, defaulting to `localhost:9092`. A real `.env` file is ignored and never committed.

Kafka-consuming services share this validated reliability policy:

| Variable | Purpose | Default |
| --- | --- | --- |
| `KAFKA_CONSUMER_MAX_RETRIES` | Retries after the initial delivery attempt | `3` |
| `KAFKA_CONSUMER_RETRY_INITIAL_INTERVAL` | First blocking backoff interval | `PT0.25S` |
| `KAFKA_CONSUMER_RETRY_MULTIPLIER` | Exponential backoff multiplier | `2.0` |
| `KAFKA_CONSUMER_RETRY_MAX_INTERVAL` | Maximum delay between attempts | `PT2S` |
| `KAFKA_DLT_PUBLISH_TIMEOUT` | Maximum wait for broker acknowledgement of DLT publication | `PT5S` |

Observability supports:

| Variable | Purpose | Default |
| --- | --- | --- |
| `OTEL_TRACING_EXPORT_ENABLED` | Export OpenTelemetry spans to Tempo | `false` outside Compose; `true` in Compose |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | OTLP/HTTP trace ingest endpoint | `http://localhost:4318/v1/traces` |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | Fraction of traces retained, from `0.0` to `1.0` | `0.1`; Compose uses `1.0` locally |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Structured console log format used by Alloy/Loki | Human-readable outside Compose; `logstash` in Compose |
| `GRAFANA_ADMIN_USER` | Local Grafana administrator username | Development-only value from `.env` |
| `GRAFANA_ADMIN_PASSWORD` | Local Grafana administrator password | Development-only value from `.env` |

Prometheus metrics are intentionally unauthenticated for local scraping, but their ports and all service actuator ports are loopback-only. Production deployments must restrict these endpoints at the network layer and supply real secrets through the platform secret store.

## API Examples

Register credentials:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"customer@example.com","password":"correct horse battery staple"}'
```

Auth details and refresh/key behavior are documented in [Auth Service](services/auth-service/README.md).

Create or replace the authenticated customer's profile using an Auth access token:

```bash
curl -X PUT http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"displayName":"Linh Nguyen","phone":"+84901234567"}'
```

The client cannot supply a user ID; User Service derives it from the verified JWT subject. See [User Service](services/user-service/README.md).

Create a product:

```bash
curl -i -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: readme-demo" \
  -d '{"sku":"LAPTOP-001","name":"Developer Laptop","description":"Development workstation","price":1499.00,"currency":"USD","status":"ACTIVE"}'
```

Search the catalog:

```bash
curl "http://localhost:8080/api/v1/products?query=laptop&status=ACTIVE&page=0&size=20&sortBy=price&direction=ASC"
```

Product details and the stable error contract are documented in [Product Service](services/product-service/README.md).

Create an idempotent order using a customer access token:

```bash
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer CUSTOMER_ACCESS_TOKEN" \
  -H "Idempotency-Key: checkout-request-001" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"PRODUCT_UUID","quantity":2}]}'
```

The response is `202 Accepted` with a `PENDING` order and trusted product snapshots. Reuse the same key only for the same logical request.

Create stock using a Product Service UUID, then reserve it for an Order UUID:

```bash
curl -X PUT http://localhost:8080/api/v1/inventory/items/PRODUCT_UUID \
  -H "Authorization: Bearer ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" -d '{"totalQuantity":10}'

curl -X POST http://localhost:8080/api/v1/inventory/reservations \
  -H "Authorization: Bearer ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER_UUID","items":[{"productId":"PRODUCT_UUID","quantity":2}]}'
```

Inventory contracts and state semantics are documented in [Inventory Service](services/inventory-service/README.md).

## Testing

Run every implemented service suite:

```powershell
.\mvnw.cmd test
```

Product has 28 tests covering cache hits/misses, after-commit invalidation, fail-open behavior, cache metrics, batch access, and real Redis/PostgreSQL lifecycle behavior; Inventory has 29 including concurrent reservation plus real Kafka/PostgreSQL success and DLT flows, Auth has 20 covering cryptography/token lifecycle, configured key-pair validation, fail-fast configuration, and metrics security, User has 18 covering resource-server security, ownership, and metrics security, Gateway has 10 covering routing, edge behavior, and metrics security, Order has 56 covering acceptance, Product resilience, metrics security, outbox/inbox, strict Inventory/Payment event parsing, terminal state transitions, compensation, and real Kafka/PostgreSQL success/DLT flows, Payment has 36 covering state transitions, database constraints, provider retry/refund semantics, bounded transient retry, exhausted recovery, strict event contracts, outbox/inbox behavior, and real Kafka/PostgreSQL flows, and Notification has 18 covering delivery state, strict terminal event parsing, duplicates, provider failure, contradictory/corrupted outcomes, and real Kafka/PostgreSQL success/DLT flows. The implemented reactor currently has 215 tests. Each service uses the smallest meaningful combination of unit, controller, repository, integration, security, proxy, and Testcontainers tests.

## CI/CD

`.github/workflows/ci.yml` runs for `main`, `feature/**`, `fix/**`, pull requests to `main`, and manual dispatch. Its read-only quality job runs the complete Maven `verify` lifecycle, creates a disposable environment file, validates the rendered Compose model, and removes that file even after failure. Only after those gates pass does a matrix build all eight service images independently. Concurrency cancellation prevents stale commits on the same ref from consuming runner capacity.

Version tags matching `v*.*.*` trigger `release-images.yml`. The workflow reruns the reactor and grants `packages: write` only to the publish matrix. Each service is pushed to `ghcr.io/<owner>/<repository>/<service>` with the version tag and an immutable `sha-<12-character-commit>` tag, OCI source/revision/version labels, an SBOM, and provenance attestation. It deliberately performs no deployment: artifact creation and environment rollout have different permissions, rollback rules, and approval needs.

Third-party Actions are pinned to reviewed commit SHAs. Dependabot opens bounded weekly Maven and GitHub Actions update pull requests so those pins remain deliberate and reviewable. See [ADR 016](docs/decisions/016-ci-and-release-images.md) and [Microservice Testing and Delivery](docs/learning/22-microservice-testing-and-delivery.md).

## Documentation

- [System overview](docs/architecture/system-overview.md)
- [Service boundaries](docs/architecture/service-boundaries.md)
- [Database architecture](docs/architecture/database-architecture.md)
- [Communication policy](docs/architecture/communication.md)
- [Security architecture](docs/architecture/security.md)
- [Observability architecture](docs/architecture/observability.md)
- [Authentication flow](docs/flows/authentication-flow.md)
- [User profile flow](docs/flows/user-profile-flow.md)
- [API Gateway request flow](docs/flows/gateway-request-flow.md)
- [Product request flow](docs/flows/product-flow.md)
- [Inventory reservation flow](docs/flows/inventory-flow.md)
- [Order creation flow](docs/flows/order-creation-flow.md)
- [Payment processing flow](docs/flows/payment-processing-flow.md)
- [Notification delivery flow](docs/flows/notification-flow.md)
- [Kafka consumer failure flow](docs/flows/kafka-failure-flow.md)
- [Observability request flow](docs/flows/observability-flow.md)
- [Product cache-aside decision](docs/decisions/014-product-cache-aside.md)
- [Stable local secrets and signing-key decision](docs/decisions/015-stable-local-secrets-and-signing-key.md)
- [CI quality gates and image release decision](docs/decisions/016-ci-and-release-images.md)
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
7. [Identity and Resource Ownership](docs/learning/07-identity-and-resource-ownership.md)
8. [API Gateway and Edge Security](docs/learning/08-api-gateway-and-edge-security.md)
9. [Synchronous Communication, Transaction Boundaries, and Idempotency](docs/learning/09-synchronous-communication-and-idempotency.md)
10. [Payment Side Effects and Idempotency](docs/learning/10-payment-side-effects-and-idempotency.md)
11. [Transactional Outbox and Idempotent Consumers](docs/learning/11-transactional-outbox-and-idempotent-consumers.md)
12. [Saga Compensation and Eventual Consistency](docs/learning/12-saga-compensation-and-eventual-consistency.md)
13. [Asynchronous Notifications and Delivery Semantics](docs/learning/13-asynchronous-notifications-and-delivery-semantics.md)
14. [Docker Compose and Service Networking](docs/learning/14-docker-compose-and-networking.md)
15. [Timeouts, Retries, and Circuit Breakers](docs/learning/15-timeouts-retries-and-circuit-breakers.md)
16. [Correlation IDs](docs/learning/16-correlation-ids.md)
17. [Distributed Tracing](docs/learning/17-distributed-tracing.md)
18. [Metrics and Centralized Logging](docs/learning/18-metrics-and-centralized-logging.md)
19. [Kafka Retries, Dead Letters, and Replay](docs/learning/19-kafka-retries-dead-letters-and-replay.md)
20. [Redis Cache-Aside and Staleness](docs/learning/20-redis-cache-aside-and-staleness.md)
21. [Configuration, Secrets, and Signing-Key Rotation](docs/learning/21-configuration-secrets-and-key-rotation.md)
22. [Microservice Testing and Delivery](docs/learning/22-microservice-testing-and-delivery.md)
23. Read the ADRs and compare their alternatives.
24. Follow the Gateway, Auth, User, Product, Inventory, Order, Payment, and Notification READMEs from adapters to application services, domains, repositories, migrations, and tests.

Later notes will reference the exact service, class, endpoint, migration, event, and configuration that implements each concept.

## Engineering Workflow

- `main` remains stable.
- Significant work uses focused feature branches when isolation is useful.
- Logical milestones use Conventional Commits.
- Relevant builds/tests, diff review, secret scan, and self-review are required before push.
- No force pushes or rewritten published history.
- Simplicity and justified decisions matter more than the number of technologies.
