# ADR 015: Bootstrap Stable Local Secrets and JWT Signing Configuration

## Status

Accepted and implemented for the Docker Compose environment.

## Context

An ephemeral Auth signing key is safe for an isolated demo because no private key is committed. It is unsafe for the connected environment: restarting Auth changes the public key while Gateway, User, and Order may still cache the previous JWKS. New tokens then fail against stale caches, and old tokens fail once verifiers fetch the replacement key. Copying placeholder passwords from a template also makes local credentials predictable.

Production uses an external secret manager, but requiring one for the local learning stack would add unrelated operational cost. The repository still must not contain private keys or real passwords.

## Options considered

1. Generate an RSA key at every Auth startup: zero setup, but routine restarts invalidate tokens and conflict with JWKS caching.
2. Commit a shared development private key: stable and easy, but trains unsafe secret handling and leaks signing authority to every clone.
3. Generate ignored local secrets once and require them in Compose: stable restarts and realistic injection without committing secrets.
4. Add Vault or a cloud secret manager locally: closest to some production environments, but unjustified complexity at this stage.

## Decision

Choose option 3.

- `scripts/bootstrap-local-env.ps1` reads `.env.example`, replaces credential placeholders with independent random values, generates a 2048-bit RSA pair, derives a non-secret `kid` fingerprint, and atomically writes ignored `.env`.
- The script refuses to overwrite an existing file unless `-Force` is explicit. It never prints secret values.
- Compose requires the key ID, PKCS#8 private key, and X.509 public key and forces configured-key mode. Missing values fail during configuration instead of silently weakening runtime behavior.
- Auth parses both keys and verifies that their RSA moduli match before startup completes.
- A direct non-Compose development process may retain the documented ephemeral fallback. The `prod` profile and Compose may not.
- Production must inject the same configuration contract through its secret facility; the local `.env` file is not a production secret store.

## Consequences

- Routine Auth container restarts preserve outstanding access-token validity and verifier JWKS caches.
- Each checkout gets different credentials and signing authority without storing them in Git.
- Deleting or force-regenerating `.env` intentionally invalidates current tokens and may require recreating databases if database passwords also change.
- The current Auth JWKS publishes one key. Zero-downtime production rotation still requires multi-key overlap, rollout orchestration, audit, and rollback procedures.
- Process environment variables and local files remain visible to sufficiently privileged host users; local bootstrap improves hygiene but is not a hardware-backed production solution.
