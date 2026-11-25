# TV2 Data Dictionary – PostgreSQL

| Table | Purpose | Primary key | Important columns / constraints |
|---|---|---|---|
| `product_category` | Product taxonomy | `category_id` | `category_code` unique, `name`, `status` |
| `product` | Product catalogue | `product_id` | `category_id` FK, `sku` unique, `price > 0`, `unit`, `low_stock_threshold`, `status` |
| `machine_location` | Machine site/location | `location_id` | `location_code` unique, `name`, `address`, `status` |
| `vending_machine` | Machine master | `machine_id` | `location_id` FK, `machine_code` unique, `status` |
| `machine_slot` | Slot/column on machine | `slot_id` | `machine_id` FK, unique `(machine_id, slot_code)`, nullable `product_id`, `capacity`, `status` |
| `inventory_batch` | Received lot and expiry | `batch_id` | `product_id` FK, `batch_code`, `received_date`, `expiry_date`, `initial_quantity`, `status` |
| `inventory_stock` | Current quantity by batch and slot | `stock_id` | `batch_id`, `product_id`, `slot_id`, `quantity_on_hand >= 0`, `version`; composite FKs enforce product consistency |
| `restock` | Restock transaction | `restock_id` | unique `restock_code`, batch/product/slot FKs, positive quantity, actor and timestamp |
| `inventory_movement` | Append-only stock movement history | `movement_id` | movement type, signed quantity delta, batch/product/slot, reference, actor, time |
| `inventory_alert` | Low-stock/expiry alerts | `alert_id` | alert type, severity, product/batch/slot, status, timestamps |
| `audit_log` | Operator/admin action history | `audit_id` | actor, action, entity, old/new JSONB, reason, result, timestamp |
| `failed_job` | Retry/dead-letter job records | `job_id` | unique `job_key`, payload JSONB, attempts, status, retry time and error |

## Integrity rules

- `inventory_stock` is the current balance; `inventory_movement` is the history.
- Restock/issue code updates `inventory_stock` and appends `inventory_movement` in one transaction.
- A slot must be assigned to the same product as any batch stock placed in that slot; composite foreign keys enforce this.
- The DDL does not define TV4 telemetry/temperature tables. TV4 owns device telemetry and machine monitoring.
- Actor IDs are references to the shared Auth layer; TV1 owns user/role schema.
