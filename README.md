# E-commerce Backend: Product Catalog

The product catalog service for a single-seller online store. It stores and serves everything a shopper needs to find and evaluate products (variants, prices in EUR and USD, stock levels, categories, images) and gives staff the tools to create, edit, publish and retire them.

Built with **Spring Boot** and **MySQL**, using **spec-driven development**: the specification is written and agreed first, and every piece of code traces back to it.

---

## Status

| Stage | State |
|---|---|
| Requirements, API contract, data model, tests, implementation plan | ✅ Complete |
| Implementation | ⏳ Starting at task T-01 |

---

## The specification

**[`docs/spec/catalog/spec.md`](docs/spec/catalog/spec.md) is the source of truth.** If the code and the spec disagree, the spec wins, or it gets updated first.

| Part | Contents |
|---|---|
| 1. Requirements | What the catalog must do (`CAT-FR-001` … `CAT-FR-101`), quality targets (`CAT-NFR-001` … `008`), decisions log (D1–D29) |
| 2. API contract | Every endpoint: public (P1–P7), admin (A1–A26), internal (I1–I2), with status codes and error format |
| 3. Data model | MySQL tables, keys, constraints and the reasoning behind each design choice |
| 4. Test specification | 107 test cases, each traced to a requirement (`TC-CAT-032-01` tests `CAT-FR-032`) |
| 5. Implementation plan | 29 tasks in 6 milestones, each listing the requirements it delivers and the tests that prove it |

### How the IDs connect

```
CAT-FR-032 (requirement)  ──▶  TC-CAT-032-01 (test)  ──▶  T-12 (task)  ──▶  code + commit
```

Test method names include their test ID, and a build check flags any requirement without a test.

---

## Tech stack

| Area | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 3.x |
| Build | Maven |
| Database | MySQL 8.0.16+ (InnoDB, utf8mb4) |
| Migrations | Flyway |
| File storage | MySQL (`stored_file` table), behind a storage interface |
| Stock events | RabbitMQ in production, when inventory runs as a separate service |
| Tests | JUnit 5 against a real, locally installed MySQL |

**No Docker.** The project uses no container tools. MySQL is the only service you need installed.

---

## Getting started

### Prerequisites

- Java 21 (JDK)
- MySQL 8.0.16 or later, installed and running

### 1. Create the databases

Log in to MySQL as an administrator and run:

```sql
-- Development database
CREATE DATABASE catalog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Test database: wiped and rebuilt automatically at the start of every test run
CREATE DATABASE catalog_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Application user (choose your own password)
CREATE USER 'catalog_app'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON catalog.*      TO 'catalog_app'@'localhost';
GRANT ALL PRIVILEGES ON catalog_test.* TO 'catalog_app'@'localhost';
```

> The audit log is append-only (spec DM-11). Task T-06 narrows the application's permissions on the `audit_log` table to insert and read only; the exact grants will be added here when T-06 is done.

### 2. Configure the connection

Connection settings (URL, user, password) are documented here once T-01 and T-03 are complete.

### 3. Build, test, run

Commands are added here after T-01 (they will use the Maven wrapper, `./mvnw`).

---

## Project structure

```
.
├── CLAUDE.md                     Working rules for Claude Code
├── README.md
├── docs/
│   └── spec/catalog/spec.md      The specification
└── src/
    ├── main/java/.../
    │   ├── brand/                Brands
    │   ├── category/             Category tree
    │   ├── attribute/            Attribute templates and values
    │   ├── product/              Products, variants, options, slugs, lifecycle
    │   ├── pricing/              Prices, sales, effective price
    │   ├── media/                Image upload, storage and delivery
    │   ├── stock/                Stock snapshot, inventory events, reconciliation
    │   ├── listing/              Precomputed listing table, browse and facets
    │   ├── importer/             Bulk CSV import
    │   ├── audit/                Change history
    │   └── common/               Error format, security, shared conventions
    └── main/resources/db/migration/   Flyway migration files
```

Code is organised **by feature, not by layer**, so everything about one concept (e.g. pricing) lives in one place.

---

## How we work

1. **One task at a time**, in the order of Part 5 of the spec.
2. **Plan first:** list the files and tests before changing anything.
3. **Test-first:** write the task's listed tests, watch them fail, then build until they pass.
4. **Spec first:** if the spec is wrong or unclear, fix the spec (and its tests) before the code.
5. **Done means:** the task's tests pass, all earlier tests still pass, and the API matches Part 2 exactly.

### Rules that apply everywhere

- Never edit an existing Flyway migration; schema changes are always a new file.
- Never use H2 or any in-memory database; tests run on real MySQL.
- Money is `BigDecimal` / `DECIMAL(12,2)` / a decimal string in JSON, never floating point.
- All timestamps are UTC, and code reads the time from an injected `Clock`.
- Every write records audit history in the same transaction.
- Code carries inline comments explaining *why*.

### Working with Claude Code

`CLAUDE.md` holds these rules so Claude Code follows them in every session. Start each task in plan mode with:

```
Implement task T-XX (Task name) from docs/spec/catalog/spec.md, Part 5.
Read Part 5's "How to work through this plan" and the T-XX entry first.
Propose your plan, then wait for my approval.
```

---

## Key design decisions

The full reasoning is in the spec; these are the ones that shape the codebase most.

| Decision | Why | Spec |
|---|---|---|
| Two currencies with explicit prices, no conversion | Clean prices; no drift with exchange rates | D5 |
| Stock owned by the inventory module; catalog only displays it | Keeps concurrent-purchase logic out of the catalog | D8, D25 |
| Stock changes pushed as events, plus a 15-minute reconciliation | Near-instant updates, with lost events repaired automatically | D25, D26 |
| Attribute values stored one row per value | New filterable attributes need no schema change | DM-3 |
| Precomputed listing table | Fast price filtering, sorting and cursor pagination | DM-9 |
| Images stored in MySQL behind a storage interface | One service to run; can move to S3 later without touching other code | D29, DM-13 |
| No hard deletes; products are archived | Old orders always resolve | DM-10 |
| Append-only audit log | History can't be edited, even by a bug | DM-11 |

---

## Roadmap

- [ ] **M0: Foundation** (T-01 – T-06): skeleton, migrations, test harness, API conventions, security, audit
- [ ] **M1: Reference data** (T-07 – T-08): brands, categories, attribute templates
- [ ] **M2: Product management** (T-09 – T-15): products, variants, prices, images, publishing
- [ ] **M3: Stock and storefront** (T-16 – T-22): stock sync, product pages, browse, filters, facets
- [ ] **M4: Bulk import and history** (T-23 – T-25): CSV import, change history
- [ ] **M5: Hardening and release** (T-26 – T-29): security sweep, performance, monitoring, release gate

---

## Out of scope

Full-text search, reviews, recommendations, cart, checkout, orders, promotions, tax, shipping, multiple sellers, and languages other than English. See Part 1, section 6 of the spec.
