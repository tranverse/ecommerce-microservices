# ADR 016: Gate Changes and Publish Immutable Service Images

## Status

Accepted and implemented.

## Context

A distributed application can compile one service while breaking another service's contract, shared build configuration, Compose wiring, or container image. Developer-only verification is not repeatable evidence for a team. Building mutable `latest` images also makes it impossible to prove which commit is running or to select an exact rollback artifact.

Artifact publication and deployment have different trust boundaries. Publishing an image needs package-registry access; changing a runtime environment needs environment credentials, rollout policy, health evaluation, and often human approval. Kubernetes deployment is not implemented yet, so coupling a release tag to an imaginary production rollout would hide those decisions.

## Options considered

1. Keep verification local: fastest to configure, but results depend on developer discipline and workstation state.
2. Test and build only changed services: cheaper, but reliable impact analysis needs an explicit dependency graph and contract-change detection that the repository does not yet have.
3. Verify the full reactor, validate Compose, build every service image, and publish only from version tags: more runner time, but simple and reliable at the current repository size.
4. Publish and automatically deploy on every merge: shorter path to an environment, but grants unnecessary authority and provides no mature rollout or rollback policy.

## Decision

Choose option 3.

- Pull requests and supported branches run all Maven unit and integration tests, generate disposable non-production configuration, validate the Compose model, and build all eight Dockerfiles.
- Quality jobs receive read-only repository permission. Image publication receives `packages: write` only in the tag-triggered publish job.
- Version tags matching `v*.*.*` publish one image per service to GHCR with both the requested version and an immutable commit-SHA tag.
- Published images include OCI source, revision, and version labels plus BuildKit SBOM and provenance attestations.
- Third-party Actions are pinned to exact commit SHAs. Dependabot proposes bounded weekly updates for Maven and Actions.
- No workflow deploys an environment. Deployment will be a separate policy after the Kubernetes milestone defines health, rollout, rollback, secrets, and approvals.

## Consequences

- Cross-service and container regressions are caught from a clean runner before release.
- A release can identify and roll back to an exact source commit.
- The full build matrix consumes more runner time than change-based builds; concurrency cancellation limits waste on superseded commits.
- Version tags become security-sensitive release inputs and should be protected by repository rules.
- Future deployment automation can consume immutable images without gaining permission to rebuild or alter them.
