# SCRUM-12C / SCRUM-32–33 – Batch, Restock, Issue and FIFO

## Batch lifecycle

`PENDING → AVAILABLE → NEAR_EXPIRY → EXPIRED`

Additional states:
- `DEPLETED`: available quantity reaches zero.
- `QUARANTINED`: held for review, for example after a reported storage excursion.
- `DISPOSED`: remaining quantity has been disposed and recorded.
- `REJECTED`: batch failed receiving inspection.

`NEAR_EXPIRY` may be represented as an alert classification while the underlying batch remains `AVAILABLE`.

## Restock flow

1. Operator selects machine and slot.
2. Operator selects the active product assigned to the slot.
3. Operator enters batch code, received date, expiry date and positive quantity.
4. System validates product/slot compatibility and `expiry_date >= received_date`.
5. System creates or identifies the batch.
6. System creates a restock record.
7. System increases stock for the `(batch, slot)` pair.
8. System records inventory movement and audit event in the same transaction.
9. System refreshes low-stock/near-expiry alerts.

## FIFO issue flow

1. Receive product, slot, requested quantity and business reference.
2. Lock eligible stock rows.
3. Filter positive stock, `batch.status = AVAILABLE`, and `expiry_date >= asOfDate`.
4. Sort by `received_date ASC`, then `expiry_date ASC`, then `batch_id ASC`.
5. Allocate from the first batch until exhausted or the request is satisfied.
6. Continue to the next eligible batch if required.
7. Persist each stock decrement and a negative inventory movement.
8. If eligible stock is insufficient, reject the entire transaction and roll back all partial allocations.

Received-date ordering is FIFO. Expiry date is a deterministic secondary order only when received dates match; this does not convert the rule to FEFO.

## State transition rules

| Current state | Event/condition | Next state | Action |
|---|---|---|---|
| PENDING | Receiving accepted | AVAILABLE | Create stock and movement. |
| PENDING | Receiving rejected | REJECTED | Record reason. |
| AVAILABLE | Within near-expiry window | AVAILABLE + alert | Raise near-expiry alert. |
| AVAILABLE | Expiry date passed | EXPIRED | Block issue and raise alert. |
| AVAILABLE | Quantity becomes zero | DEPLETED | Retain history; no further issue. |
| AVAILABLE | Authorized hold | QUARANTINED | Block issue until resolved. |
| QUARANTINED | Authorized release | AVAILABLE | Record reviewer and actor. |
| QUARANTINED | Disposal approved | DISPOSED | Record disposed quantity/reason. |

## Concurrency and integrity

- Use a database transaction for allocation.
- Lock candidate stock rows (`PESSIMISTIC_WRITE` / `SELECT … FOR UPDATE`).
- Re-check eligible quantity inside the transaction.
- Never allow stock below zero.
- Save one movement row for every batch allocation.
- A failed allocation rolls back all stock changes.
- Production POST operations should use an idempotency key; the local PoC focuses on FIFO and does not implement the team's final idempotency layer.
