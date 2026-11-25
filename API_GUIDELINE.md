# FreshVend API Guideline – Shared Draft (NEW-20)

Version 1.0 – owner TV2 – base path `/api/v1`.

This is proposed for the whole team. TV1 should confirm the final shared envelope and global exception mapping before merge.

## Naming and versioning
- REST resources use plural nouns: `/products`, `/categories`, `/machines`.
- JSON properties use lower camelCase; database tables/columns use snake_case.
- Use `/api/v1` for the current contract.
- IDs are opaque; clients must not infer business meaning from them.

## HTTP methods

| Method | Meaning |
|---|---|
| GET | Read; must not mutate state. |
| POST | Create or execute a business operation (restock/issue). |
| PUT | Replace a resource or complete assignment. |
| PATCH | Partially update mutable fields. |
| DELETE | Avoid hard deletion for records with operational history; prefer status transition. |

## Success envelope

```json
{
  "success": true,
  "data": {},
  "meta": {
    "timestamp": "2026-11-25T10:00:00+07:00",
    "traceId": "trace-id"
  }
}
```

Paginated `meta` also contains `page`, `size`, `totalElements`, `totalPages`, `sort`.

## Error envelope

```json
{
  "success": false,
  "error": {
    "code": "INVENTORY_INSUFFICIENT_STOCK",
    "message": "Available stock is insufficient.",
    "details": [
      { "field": "quantity", "reason": "Requested quantity exceeds eligible stock." }
    ]
  },
  "meta": {
    "timestamp": "2026-11-25T10:00:00+07:00",
    "traceId": "trace-id"
  }
}
```

Use stable machine-readable codes. Never expose stack traces, SQL, secrets or internal hostnames.

## Pagination and filtering
- `page`: zero-based; default `0`.
- `size`: default `20`; maximum `100`.
- `sort`: allow-listed field and direction, e.g. `createdAt,desc`.
- Never concatenate raw client sort strings into SQL.

## Validation and HTTP status
- `400 VALIDATION_ERROR`: malformed fields.
- `401 UNAUTHENTICATED` / `403 FORBIDDEN`: shared TV1 Auth/RBAC.
- `404 RESOURCE_NOT_FOUND`: unknown IDs.
- `409 BUSINESS_CONFLICT`: duplicate SKU, occupied slot or insufficient stock.
- `422 BUSINESS_RULE_VIOLATION`: invalid batch dates or expired batch issue.
- Use ISO-8601 dates/timestamps; persist instants consistently.

## Authentication and authorization
- Production endpoints use shared JWT/RBAC implementation owned by TV1.
- Derive actor identity from authenticated principal; do not trust a client-supplied `actorId`.
- Document required roles/scopes in OpenAPI.
- The local JPA PoC is local-only and does not implement JWT.

## Inventory transaction rules
- Restock and issue are atomic.
- Lock stock rows before decrementing.
- FIFO order: received date ascending; expiry date and batch ID are deterministic tie-breakers.
- Exclude expired, quarantined, disposed and depleted batches from issue.
- Save movement history in the same transaction as stock change.
- Failed allocation rolls back all partial allocations.
- Production POST operations should support `Idempotency-Key`; the local PoC does not implement the shared idempotency store.

## API documentation
- Update `openapi-v1.yaml` in the same Pull Request as API changes.
- Publish Swagger UI via the shared Spring Boot `springdoc-openapi` configuration.
- Breaking changes require a new API version or documented migration plan.
