# FreshVend – TV2 Git Commit Kit

**TV2 scope:** Product, Inventory & Machine Slot.  
**Technology direction:** Java 17+, Spring Boot 3.x, Spring Data JPA, PostgreSQL.

This folder is structured to be copied into the root of the team's FreshVend Git repository. It contains source artifacts, not Jira upload forms.

## Deliverables mapped to the assignment

| Task | Git artifact |
|---|---|
| SCRUM-12A, SCRUM-12B, NEW-05, NEW-26 | `docs/tv2/requirements/TV2_REQUIREMENTS.md` |
| NEW-24 | `docs/tv2/design/NEW-24-storage-research.md` |
| SCRUM-12C, SCRUM-32–33 | `docs/tv2/design/FIFO_AND_BATCH_RULES.md` + Activity/State diagrams |
| NEW-25, SCRUM-14A | `docs/tv2/requirements/TV2_REQUIREMENTS.md` |
| NEW-27 | `data/tv2/` + SQL seed data |
| NEW-18A-2 | `docs/tv2/diagrams/rendered/use-case-product-inventory.svg` |
| SCRUM-30, SCRUM-31, NEW-11 | `database/tv2/data_dictionary.md` + ERD |
| NEW-20 | `api/tv2/API_GUIDELINE.md` |
| NEW-39 | `docs/tv2/design/NEW-39-background-jobs.md` + `failed_job` DDL |
| NEW-38A | `database/tv2/TV2_DDL_Seed_PostgreSQL.sql` |
| NEW-18B-2 | `docs/tv2/diagrams/rendered/class-product-inventory-slot.svg` |
| NEW-19A-2 / NEW-19B-2 | Review checklists marked pending TV4 artifacts |
| SCRUM-34 | `api/tv2/openapi-v1.yaml` |
| NEW-12 / NEW-10 | `docs/tv2/wireframes/*.svg` + user flows |
| NEW-46 | `poc/tv2-jpa-fifo/` – Spring Boot JPA FIFO proof of concept |
| NEW-47 | `tests/tv2/NEW-47_Test_Cases.csv` + Java tests in PoC |
| NEW-48 / NEW-49 / NEW-50 | Module SRS/Design, backlog and Operator Manual outline |

## Repository layout

```text
api/tv2/                 API guideline and OpenAPI contract
database/tv2/            PostgreSQL DDL, seed data and data dictionary
data/tv2/                CSV sample data
docs/tv2/requirements/   FR, NFR, stories, acceptance criteria, business rules
docs/tv2/design/         FIFO, jobs, SRS/design, backlog, manual outline
docs/tv2/diagrams/       Mermaid/PlantUML/DOT sources and SVG renderings
docs/tv2/wireframes/     SVG wireframes and user flows
docs/tv2/reviews/        TV4 cross-review checklists (pending external artifacts)
poc/tv2-jpa-fifo/        Java 17 / Spring Boot 3.3.5 / Spring Data JPA PoC
tests/tv2/               Test case register
```

## Run the PostgreSQL + JPA PoC (Windows PowerShell)

Requirements: JDK 17+, Maven 3.9+, Docker Desktop (for PostgreSQL).

From the repository root:

```powershell
cd poc/tv2-jpa-fifo
docker compose up -d
mvn test
mvn spring-boot:run
```

PostgreSQL is exposed on `localhost:5433`. On first initialization it runs `database/tv2/TV2_DDL_Seed_PostgreSQL.sql`. Tests use H2 and do not require Docker.

Example request:

```powershell
$body = @{
  productId = 1
  slotId = 1
  quantity = 7
  asOfDate = "2026-11-25"
  referenceId = "DEMO-ISSUE-001"
  actorId = "tv2-demo"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/inventory/issues" `
  -ContentType "application/json" -Body $body
```

## Git workflow

The assignment's convention is `main` (stable), `develop`, and `feature/<module>-<description>`, merged through a Pull Request with at least one reviewer.

After copying this folder into the team's repository root:

```bash
git checkout develop
git pull
git checkout -b feature/tv2-product-inventory
git add api/tv2 database/tv2 data/tv2 docs/tv2 poc/tv2-jpa-fifo tests/tv2
git status
git commit -m "feat(tv2): add product inventory design and FIFO PoC"
git push -u origin feature/tv2-product-inventory
```

## Important boundaries and validation status

- TV2 owns Product, Inventory, Batch, Restock, Machine/Location/Slot, Operator Mobile wireframes and API Guideline.
- TV4 owns ESP32, sensors, temperature telemetry, door/jam monitoring and hardware control. TV2 only consumes the relevant machine/slot status and alerts.
- TV1 owns JWT/Auth/RBAC implementation. TV3 owns Order/Payment.
- The 2–8°C range is a project survey reference, not asserted as a universal legal limit for all fruit products.
- Cross-review tasks NEW-19A-2 and NEW-19B-2 are marked **PENDING** until TV4's actual ERD/API/wireframe artifacts are supplied.
- OpenAPI YAML and SVG XML are syntax-validated during package generation.
- Maven is not installed in the generation environment. Run `mvn test` locally before opening the PR; this package does not claim a successful Maven build.
