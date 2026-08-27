# System Overview

Status: **Accepted target architecture; implementation is incremental.**

## Business goal

The platform supports a customer journey from registration and login through product browsing, inventory reservation, simulated payment, order confirmation or cancellation, and notification. The system is designed as one connected distributed workflow rather than unrelated CRUD applications.

## Current state

Auth, Product, and Inventory Services are implemented, tested against PostgreSQL, containerized, and documented. Auth currently owns the token lifecycle; User profile provisioning and Gateway/resource-server enforcement are not connected yet. Direct Product-to-Inventory integration is intentionally absent: the future Order workflow will coordinate their independent data through APIs/events. The remaining components in the diagram are target architecture. A component is not considered implemented until its code, tests, runtime configuration, and documentation are present.

## Target architecture

```mermaid
flowchart LR
    Client[Web or mobile client] -->|HTTPS| Gateway[API Gateway]

    Gateway --> Auth[Auth Service]
    Gateway --> User[User Service]
    Gateway --> Product[Product Service]
    Gateway --> Order[Order Service]
    Gateway --> Inventory[Inventory Service<br/>admin APIs]

    Order -->|REST: trusted product snapshot| Product

    Auth --> AuthDB[(auth_db)]
    User --> UserDB[(user_db)]
    Product --> ProductDB[(product_db)]
    Inventory --> InventoryDB[(inventory_db)]
    Order --> OrderDB[(order_db)]
    Payment --> PaymentDB[(payment_db)]
    Notification --> NotificationDB[(notification_db)]

    Order -->|commands and events| Kafka[(Kafka)]
    Kafka --> Inventory
    Inventory --> Kafka
    Kafka --> Payment[Payment Service]
    Payment --> Kafka
    Kafka --> Order
    Kafka --> Notification[Notification Service]
```

## Architectural rules

1. The API Gateway is the public entry point and contains no core business rules.
2. Each stateful service exclusively owns its database and local transactions.
3. No cross-service foreign keys, JPA relationships, repository calls, or database queries are allowed.
4. Synchronous REST is used only when the caller needs an immediate answer.
5. Kafka is used for long-running workflows and decoupled reactions where eventual consistency is acceptable.
6. Order Service orchestrates the order saga and owns its state machine.
7. Events are versioned contracts, not serialized JPA entities.
8. Event consumers assume at-least-once delivery and implement idempotency.
9. Correlation IDs cross HTTP, events, and logs.
10. Optional infrastructure is added only after a demonstrated need.

## Main flows

### Product browsing

```text
Client -> Gateway -> Product Service -> product_db
```

This flow is synchronous because the client needs an immediate catalog response.

### Order creation

```mermaid
sequenceDiagram
    actor Client
    participant G as API Gateway
    participant O as Order Service
    participant P as Product Service
    participant K as Kafka
    participant I as Inventory Service
    participant Pay as Payment Service
    participant N as Notification Service

    Client->>G: POST /api/v1/orders
    G->>O: Authenticated request + correlation ID
    O->>P: Fetch active products and trusted prices
    P-->>O: Product snapshots
    O->>O: Save PENDING order + outbox command
    O-->>Client: 202 Accepted
    O->>K: InventoryReservationRequested
    K->>I: Reserve inventory
    I->>K: InventoryReserved or InventoryReservationFailed
    K->>O: Reservation result
    O->>K: PaymentRequested when reserved
    K->>Pay: Process simulated payment
    Pay->>K: PaymentCompleted or PaymentFailed
    K->>O: Payment result
    O->>K: OrderConfirmed or compensation commands
    K->>N: Customer notification event
```

The flow is eventually consistent. A local `@Transactional` method cannot atomically update Order, Inventory, and Payment databases. The saga records progress and emits compensating commands when a later step fails.

## Deployment view

Local development will use Docker Compose, one container per application, Kafka, and a PostgreSQL server hosting separate logical databases and users. Production deployment may isolate databases physically without changing service ownership.

Kubernetes is intentionally deferred until the complete Docker Compose environment works and has passing end-to-end validation.
