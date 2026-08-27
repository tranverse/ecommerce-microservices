# E-commerce Microservices

A production-style e-commerce system built incrementally to learn microservice architecture with Java 21 and Spring Boot 3.

## Current scope

The repository currently contains only the initial `product-service` scaffold. Business APIs, persistence, security, inter-service communication, messaging, and deployment infrastructure will be introduced one problem at a time.

## Services

| Service | Responsibility | Status |
| --- | --- | --- |
| API Gateway | External routing and edge concerns | Planned |
| Auth Service | Authentication and token lifecycle | Planned |
| User Service | Customer profiles and addresses | Planned |
| Product Service | Product catalog and pricing | Scaffolded |
| Order Service | Order lifecycle and price snapshots | Planned |
| Inventory Service | Stock and reservations | Planned |
| Notification Service | Asynchronous customer notifications | Planned |

Each stateful service will exclusively own its data. Services must not access another service's database or share JPA entities.

## Prerequisites

- Java 21
- PowerShell or another shell capable of running the Maven Wrapper

Maven does not need to be installed globally.

## Build and test

```powershell
cd product-service
.\mvnw.cmd test
```

## Run Product Service

```powershell
cd product-service
.\mvnw.cmd spring-boot:run
```

The service listens on port `8083` by default. Override it with the `SERVER_PORT` environment variable.

Verify its health endpoint:

```powershell
Invoke-RestMethod http://localhost:8083/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Engineering workflow

- Changes are kept small and use Conventional Commits.
- Relevant tests and builds must pass before a commit is pushed.
- Architecture and learning documentation will be added as the corresponding concepts are introduced.
- Secrets, local environment files, IDE metadata, and generated build output must not be committed.
