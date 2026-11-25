# NEW-48 – SRS / System Design Module Chapter

Version 1.0 – TV2 Product, Inventory & Machine Slot.

## Purpose

Manage product/category data, stock by batch and machine slot, restock/issue movements, expiry/low-stock alerts, slot assignment and operator audit history. Expose REST APIs for Web App and Operator Mobile App.

## Actors

- **Admin:** product/category lifecycle, inventory overview, thresholds/configuration and audit review.
- **Operator:** view inventory, restock, update price, assign product to slot and inspect alerts/history subject to TV1 permissions.
- **Order/Payment service (TV3):** requests inventory issue/availability during sale workflow.
- **Machine/Monitoring service (TV4):** supplies machine/slot status and temperature/door-related events.

## Main entities

`ProductCategory`, `Product`, `MachineLocation`, `VendingMachine`, `MachineSlot`, `InventoryBatch`, `InventoryStock`, `Restock`, `InventoryMovement`, `InventoryAlert`, `AuditLog`, `FailedJob`.

Canonical fields and constraints are in `database/tv2/data_dictionary.md`.

## Main use cases

1. Create/update/deactivate product and category.
2. Search product catalogue.
3. View stock by machine, slot, product and batch.
4. Receive stock and create/identify a batch.
5. Allocate stock by FIFO during issue.
6. Detect near-expiry, expired and low-stock conditions.
7. Assign product to a machine slot.
8. Update product price.
9. Inspect stock movement and audit history.
10. Inspect/retry failed background jobs under authorized control.

## Data and transaction design

- PostgreSQL is the relational store.
- `inventory_batch` identifies each received lot.
- `inventory_stock` stores current quantity by batch and slot.
- `inventory_movement` is append-only movement history.
- Restock and issue are transactional.
- FIFO candidates are locked before decrement to avoid concurrent overselling.
- DDL and synthetic seed records are in `database/tv2/TV2_DDL_Seed_PostgreSQL.sql`.

## API and UI

All endpoints are versioned under `/api/v1`; contract: `api/tv2/openapi-v1.yaml`. Common response/error envelope and validation rules: `api/tv2/API_GUIDELINE.md`.

NEW-12 Operator Mobile wireframes include login, dashboard/alerts, restock, price update, slot assignment, and machine/slot status plus temperature/door history views. The latter are read-only TV4 data.

## Ownership boundaries

| Dependency | Owner | TV2 integration |
|---|---|---|
| JWT, user, role, permission implementation | TV1 | Consume authenticated principal and roles. |
| Order/payment/refund | TV3 | Expose inventory issue/availability contract. |
| Machine telemetry, temperature, door, jam, device state | TV4 | Consume machine/slot status and alert context. |
| Web Admin/Operator screens | TV4 | Share API contract and operational rules. |
| Operator Mobile wireframe/API | TV2 | Define mobile flow and API contract. |

## Open decisions

- Confirm final Machine/Slot ownership and IDs with TV4.
- Confirm final product catalogue and validated shelf-life policy.
- Confirm near-expiry window and low-stock thresholds.
- Confirm shared API envelope/error codes with TV1.
- Confirm mobile technology with TV1/instructor; these files are wireframes, not a native Android implementation.
