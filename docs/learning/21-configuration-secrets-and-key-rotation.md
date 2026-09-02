# 21. Configuration, Secrets, and Signing-Key Rotation

## Concept

Configuration changes behavior without rebuilding an application. A secret is configuration whose disclosure grants access or authority: database passwords, API credentials, and JWT private keys are examples. Secret values need controlled generation, storage, injection, rotation, and audit; a template may document variable names but must not contain usable credentials.

## Why Microservices Need This

A monolith often has one runtime configuration and one restart boundary. This system has eight processes, seven database identities, multiple cached JWKS clients, and independent deployments. A value that changes unexpectedly in one service can break otherwise healthy peers. The important property is not merely “the app starts”; it is that every participant sees a compatible configuration for the intended rollout window.

Auth illustrates the issue. Gateway normally validates JWTs locally with a cached public key. If Auth creates a new signing key on every restart, the two services temporarily disagree even though both are individually healthy:

```text
Auth A signs token with key K1 -> Gateway caches public K1 -> validation succeeds
Auth restarts with K2          -> Gateway may still cache K1
new K2 token + cached K1       -> 401
old K1 token + refreshed K2    -> 401
```

Stable configuration fixes routine restart. Rotation is a different operation and needs overlap.

## Current Local Flow

```text
.env.example
      |
      v
bootstrap-local-env.ps1
  - random database/Grafana passwords
  - RSA private/public pair
  - kid derived from public-key fingerprint
      |
      v
ignored .env -> Docker Compose -> Auth validates pair -> JWKS publishes public key
```

Run the bootstrap once:

```powershell
.\scripts\bootstrap-local-env.ps1
docker compose up --build -d
```

The script writes atomically, protects an existing `.env`, and does not print secrets. Compose fails before starting Auth when required signing values are absent. Direct standalone Auth can still generate an ephemeral key for a small experiment, while Compose and `prod` require explicit configuration.

## Safe Rotation Model

Changing the private key and deleting the old public key simultaneously is a breaking deployment. A production rotation should:

1. generate a new pair and distinct `kid`;
2. publish both old and new public keys;
3. make every verifier able to refresh the expanded JWKS;
4. switch signing to the new private key;
5. wait longer than the maximum access-token lifetime plus cache/clock allowance;
6. remove the old public key and retire its private key;
7. preserve audit evidence and a tested rollback path.

The current single-key Auth implementation deliberately stops at stable restart. It does not pretend that replacing `.env` is zero-downtime rotation.

## Production Differences

- A deployment platform injects secrets from Vault, a cloud secret manager, sealed/encrypted configuration, or an equivalent controlled system.
- Access follows least privilege: only Auth receives the signing private key; resource servers consume JWKS public keys.
- Secrets are encrypted at rest and in transit, access is audited, and rotation is automated and rehearsed.
- Kubernetes `Secret` is an injection object, not automatically a complete secret-management strategy; cluster encryption, RBAC, external synchronization, and backup handling still matter.
- Logs, traces, metrics, crash reports, shell history, and CI output must not expose secret values.

## Review Questions

1. Why is committing a “development-only” private signing key still dangerous?
2. Why does an Auth restart affect Gateway even though JWT validation is local?
3. What is the difference between stable restart and key rotation?
4. Why must the old public key remain available after signing switches to the new key?
5. Which services need the private key, and which need only JWKS?

## Practical Exercise

Issue an access token through Gateway, record only its expiry and `kid`, restart Auth without changing `.env`, and call a protected endpoint with the same token. It should still succeed. Then explain why deliberately regenerating `.env` can invalidate the token and why production rotation needs a two-key overlap instead.
