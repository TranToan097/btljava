# NEW-49 – Product Backlog: Product / Inventory / Operator

Story points are preliminary estimates for the next implementation sprint; re-estimate with the team.

| ID | Story | Priority | SP | Dependency |
|---|---|---:|---:|---|
| US-P01 | Create product/category | High | 3 | TV1 Auth |
| US-P02 | Search/filter product catalogue | High | 3 | Product API |
| US-P03 | Update/deactivate product | Medium | 2 | Audit |
| US-I01 | View stock by machine/slot/batch | High | 5 | TV4 Machine IDs |
| US-I02 | Record restock by batch | High | 5 | Slot assignment |
| US-I03 | FIFO issue allocation | Critical | 8 | TV3 order reference |
| US-I04 | View stock movement history | Medium | 3 | Inventory movement |
| US-I05 | Near-expiry alert | High | 5 | Background job |
| US-I06 | Low-stock alert | High | 3 | Threshold config |
| US-I07 | Reconcile inventory | Medium | 5 | Movement history |
| US-O01 | Operator updates price | Medium | 3 | RBAC / Audit |
| US-O02 | Operator assigns product to slot | High | 5 | TV4 Machine/Slot |
| US-O03 | Operator views alerts | High | 3 | Alert API |
| US-O04 | Operator views machine/slot status | Medium | 3 | TV4 telemetry API |
| US-O05 | Admin reviews audit log | Medium | 3 | Audit API |
| US-J01 | Admin reviews/retries dead-letter jobs | Low | 5 | TV1 RBAC |

## Definition of Done

- Acceptance criteria pass.
- Validation and error responses follow shared API guideline.
- Stock-changing operations are transactional and tested.
- Audit/movement records are created where required.
- OpenAPI contract is updated.
- Tests are green and a reviewer approves the Pull Request.
