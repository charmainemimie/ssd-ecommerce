# E-commerce Backend — Product Catalog

Spring Boot + MySQL backend, built **spec-driven**. The spec is the source of truth:
`docs/spec/catalog/spec.md`

It has five parts. Read only the parts the current task needs, not the whole file:
- Part 1 — Requirements (IDs like `CAT-FR-032`, decisions log D1–D27)
- Part 2 — API contract (endpoints P1–P6, A1–A25, I1–I2)
- Part 3 — Data model (tables, constraints, design decisions DM-1 to DM-12)
- Part 4 — Test specification (104 tests, IDs like `TC-CAT-032-01`, fixtures F-*)
- Part 5 — Implementation plan (tasks T-01 to T-29, each with its requirements and tests)

## Stack
- Java 21, Spring Boot 3.x, Maven
- MySQL 8.0.16+ (InnoDB, utf8mb4); Flyway for migrations
- Tests: JUnit 5, Testcontainers (real MySQL), MinIO container for image storage
- Stock events: RabbitMQ (see spec Part 3, section 10)

## Workflow rules
1. **One task at a time.** Work only on the task named in the prompt. Don't start the next one.
2. **Plan first.** Before editing, list the files you'll create or change and the tests you'll write, then wait for approval.
3. **Test-first.** Write the task's listed tests from Part 5 first, confirm they fail, then implement until they pass.
4. **Spec first.** If the spec is wrong, ambiguous or incomplete, stop and say so. Don't invent behaviour. The spec is updated before the code.
5. **Run the full test suite** before calling a task done. Earlier tests must still pass.
6. **Finish with a short report:** what was built, which test IDs pass, anything that needed a spec change.

## Code conventions
- Packages by feature: `brand`, `category`, `attribute`, `product`, `pricing`, `media`, `stock`, `listing`, `importer`, `audit`, `common`.
- **Comment code inline**, explaining *why*, not just *what*, so a reader can follow the reasoning.
- Every test method name includes its test ID, e.g. `TC_CAT_032_01_saleActiveDuringWindow`.
- Never edit an existing Flyway migration. Schema changes are always a new migration file.
- Never use H2 or any in-memory database substitute. Integration tests use real MySQL via Testcontainers.
- Money is `BigDecimal` in Java and `DECIMAL(12,2)` in MySQL, and a decimal string in JSON. Never `double` or `float`.
- All timestamps are UTC. Code gets the current time from an injected `Clock`, never `now()` directly.
- Every write records audit rows in the same transaction (from T-06 onwards).
- Endpoints, status codes, field names and the error format must match Part 2 exactly.

## Commands
<!-- Fill in after T-01: build, run, run tests, run one test -->
