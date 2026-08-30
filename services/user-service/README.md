# User Service

User Service owns customer profiles and addresses in `user_db`. Auth Service owns credentials, password hashes, roles, and tokens in `auth_db`. The shared value is the opaque UUID in JWT `sub`; neither service queries the other's database.

## API

All business endpoints require an RS256 access token with `CUSTOMER` or `ADMIN` role. Base path: `/api/v1/users/me`.

| Method | Path | Purpose | Success |
| --- | --- | --- | --- |
| `GET` | `/api/v1/users/me` | Read the authenticated profile and addresses | `200 OK` |
| `PUT` | `/api/v1/users/me` | Create or replace profile fields idempotently | `200 OK` |
| `POST` | `/api/v1/users/me/addresses` | Add an owned address | `201 Created` |
| `PUT` | `/api/v1/users/me/addresses/{addressId}` | Replace an owned address | `200 OK` |
| `DELETE` | `/api/v1/users/me/addresses/{addressId}` | Delete an owned address | `204 No Content` |

The API does not accept a profile ID. `UserProfileController` parses the verified JWT subject, preventing a client from selecting another profile. An address outside that subject's aggregate returns the same `404 ADDRESS_NOT_FOUND` as an unknown address, avoiding ownership disclosure.

Registration currently creates Auth credentials only. The authenticated `PUT /me` is a safe, idempotent profile-completion step. A future registration event will provision a minimal profile asynchronously; Auth will still never write `user_db`.

## Data and Transactions

`UserProfile` is the aggregate root. `UserAddress` is a lazy, cascade-owned child with orphan removal. Profile/address mutations use one local `user_db` transaction. A fetch graph loads one profile plus its addresses for response mapping while Open Session in View remains disabled, avoiding N+1 queries.

At most one address may be default. The domain clears the previous default, and PostgreSQL enforces the final invariant with a deferrable unique constraint over a generated marker. Deferral makes a valid default switch independent of Hibernate update order. Profile and address rows use optimistic versions; conflicting writes return a stable `409` rather than silently losing data.

Profiles store display name and optional E.164 phone. Addresses validate lengths, E.164 phone, and uppercase two-letter country code. User Service deliberately does not duplicate login email or password data.

## Security

- Accepts only RS256 JWTs.
- Validates exact issuer, signature, expiry, and roles.
- Downloads public JWKS with 2-second connect/read timeouts and uses the decoder cache.
- Enforces both allowed role and resource ownership.
- Returns stable JSON for 401/403 and never logs bearer tokens.

Gateway now adds coarse edge checks, but User continues validating and authorizing requests itself. Internal network location is not trust.

## Configuration

| Variable | Required | Default |
| --- | --- | --- |
| `USER_DB_PASSWORD` | Yes | None |
| `USER_DB_URL` | No | `jdbc:postgresql://localhost:5432/user_db` |
| `USER_DB_USERNAME` | No | `user_app` |
| `USER_DB_POOL_SIZE` | No | `10` |
| `USER_DB_MIN_IDLE` | No | `2` |
| `USER_AUTH_ISSUER` | No | `http://localhost:8081` |
| `USER_AUTH_JWKS_URI` | No | `http://localhost:8081/.well-known/jwks.json` |
| `USER_AUTH_CONNECT_TIMEOUT` | No | `PT2S` |
| `USER_AUTH_READ_TIMEOUT` | No | `PT2S` |
| `SERVER_PORT` | No | `8082` |

Swagger UI is available locally at `http://localhost:8082/swagger-ui.html`. It is disabled by the `prod` profile.

## Build and Test

```powershell
cmd /c mvnw.cmd -pl services/user-service test
cmd /c mvnw.cmd -pl services/user-service package
docker build -f services/user-service/Dockerfile -t ecommerce/user-service:local .
```

The 17 tests cover domain invariants, application behavior, validation, stable security errors, roles, malformed subjects, JWT-subject ownership, Flyway/Hibernate compatibility, default-address switching, and full HTTP behavior on PostgreSQL 17.6.
