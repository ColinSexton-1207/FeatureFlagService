# Slice 2 — Management API: design

Status: agreed in conversation 2026-11-05. Items marked *(builder's call)* were not discussed and
can be revisited.

## Intent

Make flags manageable after creation: inspect and replace each environment's configuration, list
flags, edit metadata, and archive. Delivered in four chunks, each independently testable, so the
slice can be validated incrementally. Evaluation (slice 3), audit and client-supplied expected
versions (slice 4), security (slice 5) and caching (slice 6) are out of scope.

## Chunk 1 — Environment configurations

| Method | Path | Result |
|---|---|---|
| GET | `/api/v1/flags/{key}/configurations` | 200, all four environments ordered by `environments.sort_order` (dev, qa, uat, prod) — ordering is explicit in the query |
| GET | `/api/v1/flags/{key}/configurations/{env}` | 200, one environment |
| PUT | `/api/v1/flags/{key}/configurations/{env}` | 200, the saved configuration |

Response fields: `flagKey`, `environment`, `enabled`, `rolloutPercentage`, `allowUsers`,
`denyUsers`, `version`, `updatedAt`, `updatedBy`.

Errors: unknown flag → 404 `FLAG_NOT_FOUND`; unknown environment → 404 `ENVIRONMENT_NOT_FOUND`;
PUT on an archived flag → 409 `FLAG_ARCHIVED`. Archived flags' configurations remain readable.

**PUT is a full replacement.** `enabled`, `rolloutPercentage`, `allowUsers`, `denyUsers` are all
required; missing or `null` → 400 `VALIDATION_FAILED`. `version`, `updatedAt`, `updatedBy` are
server-managed.

**Validation**
- `rolloutPercentage`: integer 0–100. A fractional number or a string is rejected, not coerced.
- User IDs are opaque and case-sensitive: 1–200 printable ASCII characters, no whitespace
  *(builder's call — flag-key rules would forbid upper case, contradicting case sensitivity)*.
  At most 1,000 IDs per list *(builder's call)*.
- A duplicate ID within one list → 400.
- An ID present in both lists → 400. The slice 3 evaluator still applies deny-wins as a backstop.
- List order is preserved as sent; an order-only difference counts as a change *(builder's call)*.
- A rejected request changes nothing: not the row, not `version`.

**Error shape.** One RFC 9457 ProblemDetail shape with a stable `code`. Unparseable JSON or a
wrong JSON type → `MALFORMED_REQUEST`. Missing fields, rule violations, and unknown or
server-managed fields → `VALIDATION_FAILED` with an `errors` array of `{field, message}`.

**Versioning — three separate problems**
1. *Concurrent database writes:* JPA `@Version` detects them. The service flushes inside its
   transaction and translates the optimistic-lock failure into a domain exception mapped to 409
   `CONFIGURATION_VERSION_CONFLICT`. Proven by a test that genuinely interleaves two transactions.
2. *Stale clients overwriting newer changes:* **not solved in this slice**, documented in the
   README. Slice 4 adds a client-supplied expected version.
3. *Identical PUTs:* the service compares the request against stored state itself (not relying on
   Hibernate dirty checking). No difference → nothing is written; `version`, `updatedAt`,
   `updatedBy` are unchanged and the current configuration is returned.

`updatedBy` stays `"system"` until slice 5.

## Chunk 2 — List flags

`GET /api/v1/flags?status=active|archived|all&q=&page=0&size=20`

- `status` defaults to `active`; values are case-insensitive *(builder's call)*; unknown → 400.
- `q`: optional case-insensitive substring match on key or name, max 100 chars; `%`/`_` are
  matched literally.
- `page` ≥ 0, `size` 1–100, default 20. Invalid → 400 `VALIDATION_FAILED`.
- Sorted by `key` ascending, always.
- Response envelope (not Spring's `Page` JSON): `content`, `page`, `size`, `totalElements`,
  `totalPages`.

## Chunk 3 — Update metadata

`PATCH /api/v1/flags/{key}` → 200 with the flag.

- Editable: `name`, `description`. Omitted → unchanged. `"description": null` clears it.
  `name` null/blank → 400. Same length limits as create; `name` is trimmed like create.
- `key` or `flagType` present → 400 (immutable). Any other unknown field → 400.
- A PATCH that changes nothing leaves `updatedAt` unchanged.
- Configurations are untouched. Archived flag → 409 `FLAG_ARCHIVED`. Unknown flag → 404.

## Chunk 4 — Archive

`DELETE /api/v1/flags/{key}` → 204.

- Sets `archivedAt` (and `updatedAt`). All four configuration rows are preserved unchanged.
- Repeat DELETE on an archived flag → 204, nothing changes (original `archivedAt` kept).
- Unknown flag → 404.
- An archived flag is treated as disabled in every environment — enforced by the slice 3
  evaluator. Re-activation is out of scope.

## API-wide change *(builder's call)*

Jackson is made strict for every endpoint: unknown properties rejected, no scalar coercion
(`"25"` → int, `"true"` → boolean) and no float-to-int truncation. This makes the PUT contract
enforceable and keeps error behaviour consistent; `POST /api/v1/flags` becomes stricter too.

## Testing

- Unit tests for configuration rule validation (domain), and for the error mapping of the version
  conflict.
- Testcontainers integration tests per chunk: happy paths, every rejection path asserting the
  database is unchanged, archived behaviour, identical-request no-ops, the interleaved
  optimistic-lock conflict, list paging/sorting/filters/escaping.
