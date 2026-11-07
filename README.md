# Feature Flag Service

A small feature-flag service: operators configure flags per environment (DEV, QA, UAT, PROD), and
applications ask whether a flag is enabled for a given user. Built as a modular Spring Boot monolith
on PostgreSQL.

**Stack:** Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 17 · Spring Data JPA · Flyway · Testcontainers

## Status

| Slice | Scope | State |
|---|---|---|
| 1. Foundation | Project, Compose, Flyway schema + seeded environments, `POST/GET /api/v1/flags`, integration tests, CI | ✅ |
| 2. Management | List/patch/archive flags, environment configuration endpoints | ✅ |
| 3. Evaluation | Pure evaluator, allow/deny precedence, deterministic rollout | ⏳ |
| 4. Persistence & concurrency | Audit log, optimistic locking, rollback tests | ⏳ |
| 5. Security | Roles, environment-scoped runtime credentials | ⏳ |
| 6. Cache & observability | Caffeine, post-commit invalidation, trace IDs, metrics | ⏳ |

## Prerequisites

- JDK 21
- Docker (for Compose and for the Testcontainers integration tests)

Maven is not required; use the included wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Running locally

```bash
cp .env.example .env   
docker compose up --build
```

Or run only the database in Docker and the app from your IDE / the wrapper:

```bash
docker compose up -d postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Flyway migrates the schema on startup and seeds one `default` project with the `dev`, `qa`, `uat`
and `prod` environments.

## Tests

```bash
./mvnw verify
```

`*Test` classes are unit tests (Surefire). `*IT` classes are integration tests (Failsafe) that start
a real PostgreSQL via Testcontainers, so Docker must be running.

## API (so far)

### Create a flag

```http
POST /api/v1/flags
Content-Type: application/json

{
  "key": "new-checkout",
  "name": "New Checkout Experience",
  "description": "Gradual rollout of the redesigned checkout",
  "flagType": "BOOLEAN"
}
```

`201 Created` with a `Location` header and the created flag. In the same transaction the service
creates one configuration per environment, each **disabled with 0% rollout** and empty allow/deny
lists. Environments are independent; nothing is inherited or promoted between them.

```json
{
  "id": "6f1c…",
  "key": "new-checkout",
  "name": "New Checkout Experience",
  "description": "Gradual rollout of the redesigned checkout",
  "flagType": "BOOLEAN",
  "createdBy": "system",
  "createdAt": "2026-11-04T15:00:00.123Z",
  "updatedAt": "2026-11-04T15:00:00.123Z",
  "archivedAt": null
}
```

Flag keys are lowercase letters and digits, optionally separated by single `-`, `_` or `.`
(max 100 chars). `flagType` is optional and defaults to `BOOLEAN`, the only type in v1.

### Retrieve a flag

```http
GET /api/v1/flags/new-checkout
```

Archived flags are still returned, with `archivedAt` set.

### List flags

```http
GET /api/v1/flags?status=active&q=checkout&page=0&size=20
```

| Parameter | Default | Rules |
|---|---|---|
| `status` | `active` | `active`, `archived` or `all` (case-insensitive) — works like tabs |
| `q` | none | Case-insensitive substring of key or name, max 100 chars; `%` and `_` match literally |
| `page` | `0` | Integer ≥ 0 |
| `size` | `20` | Integer 1–100 |

Results are always sorted by `key`. The response is a stable envelope:

```json
{ "content": [ { "key": "new-checkout", "...": "..." } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
```

### Update metadata

```http
PATCH /api/v1/flags/new-checkout
Content-Type: application/json

{ "name": "New Checkout", "description": null }
```

Only `name` and `description` are editable. An omitted field is unchanged; `"description": null`
clears it; `name` cannot be null or blank. `key` and `flagType` are immutable and any other field
is rejected. A patch that changes nothing leaves `updatedAt` untouched. Archived flag → `409`.

### Archive a flag

```http
DELETE /api/v1/flags/new-checkout
```

`204 No Content`. Sets `archivedAt`; never hard-deletes. All four environment configurations are
kept exactly as they were, and an archived flag is treated as disabled everywhere (enforced by the
evaluator in slice 3). Repeating the call is also `204` and keeps the original `archivedAt`.
Re-activation is not supported in v1.

### Environment configurations

```http
GET /api/v1/flags/new-checkout/configurations        # all four, ordered dev, qa, uat, prod
GET /api/v1/flags/new-checkout/configurations/prod
PUT /api/v1/flags/new-checkout/configurations/prod
Content-Type: application/json

{
  "enabled": true,
  "rolloutPercentage": 20,
  "allowUsers": ["user-100", "user-200"],
  "denyUsers": ["user-900"]
}
```

`PUT` is a full replacement: all four fields are required. It returns the saved configuration:

```json
{
  "flagKey": "new-checkout",
  "environment": "prod",
  "enabled": true,
  "rolloutPercentage": 20,
  "allowUsers": ["user-100", "user-200"],
  "denyUsers": ["user-900"],
  "version": 1,
  "updatedAt": "2026-11-05T22:54:52.792906Z",
  "updatedBy": "system"
}
```

- `version`, `updatedAt` and `updatedBy` are server-managed; sending them is a `400`.
- `rolloutPercentage` is an integer 0–100. `25.5` and `"25"` are rejected, not coerced.
- User IDs are opaque and case-sensitive: 1–200 printable ASCII characters, no whitespace, at
  most 1,000 per list. Duplicates within a list, and a user in both lists, are rejected.
- Each environment is independent: a `PUT` to `prod` never touches `dev`, `qa` or `uat`.
- A rejected request changes nothing. A request identical to the stored state also writes nothing:
  `version` and `updatedAt` stay as they were.
- Changing an archived flag's configuration → `409 FLAG_ARCHIVED`.

### Errors

Every error is an RFC 9457 problem document (`application/problem+json`) with a stable `code`:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "A flag with key 'new-checkout' already exists",
  "instance": "/api/v1/flags",
  "code": "FLAG_KEY_CONFLICT"
}
```

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field constraints violated (`errors[]` lists each field) |
| 400 | `VALIDATION_FAILED` | Unknown or read-only field in a body, or an invalid query parameter |
| 400 | `MALFORMED_REQUEST` | Body missing, not JSON, a wrong JSON type, or an unknown enum value |
| 404 | `FLAG_NOT_FOUND` | No flag with that key |
| 404 | `ENVIRONMENT_NOT_FOUND` | No environment with that key |
| 409 | `FLAG_KEY_CONFLICT` | Key already exists (enforced by a unique constraint, so it holds under concurrent creates) |
| 409 | `FLAG_ARCHIVED` | `PATCH` or configuration `PUT` on an archived flag |
| 409 | `CONFIGURATION_VERSION_CONFLICT` | Another request changed the same configuration mid-update; re-read and retry |
| 500 | `INTERNAL_ERROR` | Unexpected failure; details are logged, never returned |

## Design notes

- **Schema is owned by Flyway.** Hibernate runs with `ddl-auto=validate`, so a mapping that drifts
  from the migrations fails at startup instead of silently altering the database.
- **Seed data is a versioned migration**, so it runs exactly once per database and can't duplicate
  on restart.
- **Duplicate keys are decided by the database.** A pre-check gives a fast 409 for the common case,
  and the `(project_id, key)` unique constraint, flushed inside the transaction, handles races.
- **Entities stay in the application layer.** Controllers receive and return records, never JPA
  entities.
- **Configuration versions start at 0** and increment only when a `PUT` actually changes
  something. The service compares the request with the stored state itself rather than relying on
  Hibernate's dirty checking.
- **Concurrent writes are detected, stale clients are not (yet).** JPA `@Version` turns two
  overlapping updates into a `409 CONFIGURATION_VERSION_CONFLICT` for the loser instead of a silent
  lost update. But a client that read version 4 minutes ago and sends a `PUT` now will still
  overwrite version 5, because the request does not say which version it was based on. Slice 4
  adds a client-supplied expected version to close that gap.
- **JSON parsing is strict API-wide**: unknown properties are rejected and scalars are never
  coerced (`"25"` is not an int, `25.5` is not truncated, `"true"` is not a boolean).
- `createdBy` is `system` until the security slice supplies the authenticated principal.

### Notice

- AI has been used to generate documentation such as readme's and JavaDoc comments. AI may also be used
  for testing and debugging.