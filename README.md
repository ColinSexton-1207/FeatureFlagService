# Feature Flag Service

A small feature-flag service: operators configure flags per environment (DEV, QA, UAT, PROD), and
applications ask whether a flag is enabled for a given user. Built as a modular Spring Boot monolith
on PostgreSQL.

**Stack:** Java 21 · Spring Boot 3.5 · Maven · PostgreSQL 17 · Spring Data JPA · Flyway · Testcontainers

## Status

| Slice | Scope | State |
|---|---|---|
| 1. Foundation | Project, Compose, Flyway schema + seeded environments, `POST/GET /api/v1/flags`, integration tests, CI | ✅ |
| 2. Management | List/patch/archive flags, environment configuration endpoints | ⏳ |
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
| 400 | `MALFORMED_REQUEST` | Body missing, not JSON, or an unknown enum value |
| 404 | `FLAG_NOT_FOUND` | No flag with that key |
| 409 | `FLAG_KEY_CONFLICT` | Key already exists (enforced by a unique constraint, so it holds under concurrent creates) |
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
- **Configuration versions start at 0** (JPA `@Version`) and will increment on each committed update.
- `createdBy` is `system` until the security slice supplies the authenticated principal.

### Notice

- AI has been used to generate documentation such as readme's and JavaDoc comments. AI may also be used
  for testing and debugging.