# Auth Service

Auth Service owns credentials, password hashing, account roles, refresh-token state, and JWT signing. It does not own customer names, phone numbers, addresses, or other profile data; those belong to User Service.

## API

Base path: `/api/v1/auth`

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `POST` | `/register` | Create CUSTOMER credentials and issue a token pair | `201 Created` |
| `POST` | `/login` | Authenticate credentials and issue a token pair | `200 OK` |
| `POST` | `/refresh` | Rotate a valid refresh token | `200 OK` |
| `POST` | `/logout` | Revoke a refresh token idempotently | `204 No Content` |
| `GET` | `/.well-known/jwks.json` | Publish active public verification keys | `200 OK` |

Registration currently creates the authentication account only. User-profile provisioning is deliberately not simulated with a cross-database write; the User/Kafka milestone will connect that separate consistency boundary.

## Passwords

- Raw passwords are accepted only at register/login and never stored or logged.
- BCrypt cost 12 hashes passwords with a per-password salt.
- Login uses the same generic `INVALID_CREDENTIALS` response for unknown email, wrong password, or disabled account.
- An unknown email performs a dummy BCrypt verification to reduce account-enumeration timing differences.
- Minimum registration length is 12 characters; passphrases are allowed instead of brittle composition rules.

## Access Tokens

Access tokens are JWTs signed with RS256. Default lifetime is 15 minutes. Claims are intentionally small:

| Claim | Meaning |
| --- | --- |
| `iss` | Trusted Auth Service issuer |
| `sub` | Auth account UUID; the cross-service identity ID |
| `iat` / `exp` | Issue and expiry times |
| `jti` | Unique access-token identifier |
| `roles` | `CUSTOMER` or `ADMIN` authorization roles |

The token contains no password, password hash, refresh token, address, or profile PII. Backend resource servers will fetch public keys from JWKS and validate signature, issuer, and expiry locally. JWTs are bearer credentials: possession is authority until expiry, so clients must protect them and use TLS outside local development.

## Refresh Tokens

Refresh tokens are 256-bit URL-safe random opaque values with a 30-day default lifetime. The API returns the raw value once; `auth_db` stores only its SHA-256 hash. Every successful refresh revokes the presented token and creates a new one. Reusing a rotated, revoked, expired, unknown, or disabled-account token returns the same `401 INVALID_REFRESH_TOKEN` response.

Logout is idempotent and does not reveal whether a supplied token existed. The current schema retains revoked/expired rows for auditability; a future maintenance job may purge them according to a documented retention period.

## Signing Keys

When Auth is run directly without a configured key, it may generate an ephemeral 2048-bit RSA key for convenient isolated development. Restarting that direct process invalidates its old access tokens.

The complete Compose environment does not use that fallback. `scripts/bootstrap-local-env.ps1` generates a stable local pair in ignored `.env`, and Compose requires it. Auth validates that the supplied PKCS#8 private key and X.509 public key have the same RSA modulus before accepting traffic. Restarting Auth with the same `.env` therefore preserves token validity; intentionally replacing `.env` or running the bootstrap with `-Force` rotates the key and invalidates tokens unless an overlap rotation is implemented.

With the `prod` profile, startup fails unless both variables are provided:

- `AUTH_JWT_PRIVATE_KEY_BASE64`: PKCS#8 RSA private key, Base64 DER
- `AUTH_JWT_PUBLIC_KEY_BASE64`: matching X.509 RSA public key, Base64 DER

Production keys must come from secret management and support an operational rotation policy. Private key material is never returned through JWKS or logged.

## Configuration

| Variable | Required | Default |
| --- | --- | --- |
| `AUTH_DB_PASSWORD` | Yes | None |
| `AUTH_DB_URL` | No | `jdbc:postgresql://localhost:5432/auth_db` |
| `AUTH_DB_USERNAME` | No | `auth_app` |
| `AUTH_JWT_ISSUER` | No | `http://localhost:8081` |
| `AUTH_JWT_ACCESS_TOKEN_TTL` | No | `PT15M` |
| `AUTH_JWT_REFRESH_TOKEN_TTL` | No | `P30D` |
| `AUTH_JWT_KEY_ID` | Required in Compose/`prod` | `ecommerce-local-key` for direct local fallback |
| `AUTH_JWT_PRIVATE_KEY_BASE64` | Required in Compose/`prod` | Empty/direct local generated key |
| `AUTH_JWT_PUBLIC_KEY_BASE64` | Required in Compose/`prod` | Empty/direct local generated key |
| `AUTH_JWT_REQUIRE_CONFIGURED_KEY` | No | `false`; forced to `true` by Compose/`prod` |
| `SERVER_PORT` | No | `8081` |

## Build and Test

```powershell
cmd /c mvnw.cmd -pl services/auth-service test
cmd /c mvnw.cmd -pl services/auth-service package
docker build -f services/auth-service/Dockerfile -t ecommerce/auth-service:local .
```

The 20 tests cover domain/token behavior, password hashing, timing equalization, JWT cryptographic decoding, public-only JWKS, configured key-pair validation and fail-fast behavior, duplicate registration, generic login failure, refresh rotation/reuse, logout revocation, security-filter errors, Flyway, JPA, and full HTTP behavior on PostgreSQL 17.6.
