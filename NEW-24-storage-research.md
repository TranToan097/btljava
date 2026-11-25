# NEW-24 – Inventory, Shelf-life and Fresh-fruit Storage Survey

The TV2 assignment asks for a survey of inventory operations, expiry dates, fruit storage and food-safety requirements, with a project cold-chain reference of 2–8°C.

## Operational checkpoints

| Stage | Data to record | Risk | Suggested system control |
|---|---|---|---|
| Receiving | Product, batch code, received date/time, expiry/use-by date, quantity, operator | Untraceable or expired batch | Validate required fields and reject invalid dates/quantities. |
| Storage | Storage location/machine, temperature readings from monitoring module, observation time | Storage outside project target range | TV4 owns sensor/temperature capture; TV2 consumes alerts and exposes inventory context. |
| Slot assignment | Machine, slot, product, capacity, assigned quantity | Product/slot mismatch | Validate product assignment and slot capacity. |
| Issue | Product, batch, slot, quantity, time, reference transaction | Wrong batch or negative stock | FIFO allocation, transaction lock and movement history. |
| Expiry handling | Batch, expiry date, remaining quantity, action, operator | Expired stock remains available | Exclude expired batches from FIFO; create alert and record hold/disposal. |

## Cold-chain design note

- The assignment explicitly names 2–8°C as a project reference. Treat it as a configurable requirement to validate against the actual product, preparation process, packaging and instructor.
- TV4 owns temperature sensors, device telemetry and machine monitoring. TV2 owns the inventory relationship: which product/batch/slot is affected and how alerts are presented.
- Store temperature events in the TV4-owned data model. Do not duplicate a second temperature-history table in the TV2 DDL.
- A temperature excursion may place affected stock on hold pending operator/admin decision. Do not declare a product safe or unsafe solely from one reading.

## Food-safety and shelf-life caveat

Shelf life depends on fruit type, whole/cut state, preparation hygiene, packaging, exposure and validated storage conditions. The assignment does not provide official legal citations, product recipes or a validated shelf-life study. Therefore:
- Do not hard-code one shelf-life duration for every fruit bowl.
- Store received date and expiry/use-by date per batch.
- Use configurable near-expiry windows.
- Confirm applicable Vietnamese food-safety requirements and product-specific storage limits with authoritative sources before commercial deployment.

## System implications

1. Batch is a first-class entity.
2. Expiry date is mandatory for perishable products in this project workflow.
3. FIFO excludes expired batches.
4. Inventory is traceable by product, batch, machine and slot.
5. Low-stock and near-expiry thresholds are configurable.
6. Temperature monitoring is a TV4 integration dependency, not an ESP32 implementation task for TV2.
