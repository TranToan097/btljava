# NEW-39 – Scheduled Inventory Jobs, Retry and Dead-letter Design

The assignment requires `@Scheduled` jobs for expiry alerts, inventory synchronization/reconciliation, retry handling and a `failed_job` dead-letter table. Temperature telemetry and temperature-threshold evaluation remain TV4-owned.

## Proposed jobs

The schedules below are design proposals, not values prescribed by the assignment. Confirm them with TV1/TV4.

| Job | Proposed schedule | Work | Idempotency key |
|---|---|---|---|
| `inventory.expiry-alert-scan` | Every 15 minutes | Find active batches inside configured near-expiry window; create/update alerts. | `expiry:{batchId}:{alertDate}` |
| `inventory.expired-batch-scan` | Every 15 minutes | Mark batches past expiry as EXPIRED and block issue. | `expired:{batchId}` |
| `inventory.low-stock-scan` | Every 5 minutes or after movement | Compare stock by product/slot with configured threshold. | `low-stock:{productId}:{slotId}` |
| `inventory.reconciliation` | Daily off-peak | Compare balance with confirmed movement history and report discrepancies. | `reconcile:{businessDate}:{slotId}` |
| `jobs.retry-failed` | Every minute | Claim retryable jobs whose `next_retry_at` has arrived. | Original `job_key` |

## Retry policy

Proposed policy:
- Maximum attempts: 5.
- Exponential backoff: 1, 2, 4, 8, 16 minutes (bounded/configurable).
- Retry transient failures only (database timeout, temporary network issue, downstream 5xx).
- Do not retry validation errors or permanent authorization errors.
- After max attempts, set `DEAD_LETTER`; retain payload and error.
- Manual retry must be authorized and auditable.

## `failed_job` lifecycle

`PENDING → RETRYING → SUCCEEDED`; repeated failures eventually move to `DEAD_LETTER`.

The DDL includes `job_name`, `job_key`, `payload`, `attempts`, `max_attempts`, `status`, `next_retry_at`, `last_error`, timestamps and `completed_at`.

## Concurrency and failure handling

- Claim a job in a short transaction using `FOR UPDATE SKIP LOCKED`.
- Mark it `RETRYING`, increment attempts and commit.
- Execute work outside the claim transaction.
- Mark success or schedule the next retry in a second transaction.
- Log job ID/name/key, attempt, trace ID and sanitized error.
- Never store credentials in JSON payloads.
- Reconciliation reports discrepancies; it must not silently overwrite stock balances.
- Job-generated alerts must be idempotent.
