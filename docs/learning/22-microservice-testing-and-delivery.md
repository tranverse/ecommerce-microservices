# 22. Microservice Testing and Delivery

## What

Continuous integration (CI) turns the repository's quality rules into repeatable checks on a clean machine. Continuous delivery makes a verified, identifiable artifact available for deployment. Continuous deployment goes one step further and changes a running environment automatically.

This project implements CI and artifact delivery. It intentionally does not implement automatic deployment yet.

## Why Microservices Need This

Microservices increase the number of independently built processes, network contracts, runtime configurations, and container artifacts. A Product change can compile while breaking Order's expectations; all Java tests can pass while a Dockerfile no longer builds; every image can build while Compose contains an invalid variable.

A useful pipeline therefore verifies more than compilation. It provides evidence that source, contracts, configuration, and service images still form one releasable system.

## How It Works in a Monolith

A monolith commonly produces one deployable artifact from one build:

`text
source -> test one application -> build one artifact -> deploy one unit
`

Its integration surface is mostly in-process, one commit maps to one binary, and rollback selects one previous application version. Database migration risk still exists, but the artifact graph is small.

## How It Changes with Microservices

This repository produces eight application images. They share a source revision but retain independent runtime identities. The pipeline must answer:

- Did a change break another Maven module or contract consumer?
- Does each service still build from the repository root context?
- Can runtime configuration be rendered without committed secrets?
- Which exact commit produced each image?
- Can a future deployment select or roll back one service without guessing?

The deployment system must eventually add compatibility-aware rollout order, per-service health gates, migrations, rollback rules, and environment authorization. Those concerns do not belong in an image-build job.

## This Project

`mermaid
flowchart LR
    Change[Branch or pull request] --> Verify[Full Maven verify]
    Verify --> Config[Bootstrap disposable config]
    Config --> Compose[Validate Compose model]
    Compose --> Matrix[Build 8 service images]
    Matrix --> Review[Review and merge]
    Tag[Version tag] --> Reverify[Full Maven verify]
    Reverify --> Publish[BuildKit publish matrix]
    Publish --> GHCR[(GHCR version + SHA tags)]
    GHCR -. future explicit promotion .-> Environment[Runtime environment]
`

The branch workflow has read-only repository access. A version tag activates a separate workflow whose publish jobs alone receive package write access. Every external Action is pinned to a commit SHA. A disposable `.env.ci` demonstrates configuration completeness without reusing or exposing a developer's `.env`.

### Test and build layers

| Layer | Failure it catches | Representative mechanism |
| --- | --- | --- |
| Unit | Domain rule or state-transition regression | JUnit and Mockito |
| Slice/controller | HTTP validation, status, serialization, security | Spring test slices and MockMvc/WebTestClient |
| Repository | JPA mapping, constraints, migration/query behavior | PostgreSQL Testcontainers |
| Messaging | Contract parsing, retry, inbox/outbox, DLT behavior | Kafka Testcontainers |
| Cache integration | Real serialization, TTL, fail-open behavior | Redis Testcontainers |
| Reactor | Cross-module build and shared dependency regression | Maven `verify` from repository root |
| Configuration | Missing variables or invalid Compose wiring | Disposable secret bootstrap plus `docker compose config` |
| Container | Broken Dockerfile, context, or packaged artifact | Independent image-build matrix |

## Trade-offs

The full reactor and eight-image matrix are intentionally simple and expensive. At this scale they provide stronger confidence than a home-grown “changed service” detector. If runner time becomes material, safe optimization requires an explicit service dependency graph, contract compatibility tests, and periodic full builds.

One repository version tag releases eight images even if one service did not change. That gives a coherent platform release and still allows independent deployment by immutable image digest. Separate per-service versioning may become worthwhile only when teams and release cadences are genuinely independent.

SBOM and provenance metadata improve traceability; they do not prove an image is vulnerability-free or trustworthy by themselves. Admission policy, signature verification, scanning, and protected release inputs remain future controls.

## Common Mistakes

- Running only unit tests and discovering container/configuration failures after merge.
- Tagging every image `latest`, which destroys release and rollback identity.
- Giving every job write permissions instead of granting the smallest permission at job scope.
- Using floating third-party Action tags without reviewing what code a runner executes.
- Printing generated secrets or retaining disposable secret files as artifacts.
- Combining build and production deployment before defining health gates and rollback.
- Retrying a failed deployment by rebuilding the same version from different source.

## Interview Questions

1. Why does a microservice pipeline need contract and container checks in addition to unit tests?
2. What is the difference between continuous delivery and continuous deployment?
3. Why is a commit-SHA image tag safer than `latest` for rollback?
4. Why does the publish job need `packages: write` while the test job does not?
5. When is changed-service-only CI safe?
6. What do an SBOM and provenance attestation prove, and what do they not prove?
7. Why should deployment consume an existing image rather than rebuild source?

## Practical Exercise

Create a small validation failure in one service request DTO, add a focused test that exposes it, and push the branch. Observe which pipeline gate fails. Fix the implementation without weakening the test and verify that the full reactor and all image builds pass.

Then describe a rollback using the immutable `sha-<commit>` image tag. Identify which additional checks a Kubernetes deployment workflow would need before changing production.
