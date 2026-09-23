# Product Catalog — Specification

| | |
|---|---|
| **Version** | 0.1 |
| **Status** | Requirements agreed; API contract, data model, tests and implementation plan in draft |
| **Stack** | Spring Boot, MySQL 8.0.16+ |

This single document is the source of truth for the catalog module. Requirement IDs (e.g. `CAT-FR-001`) are referenced by the API contract, the data model, tests and commits.

## Contents

- **Part 1 — Requirements:** how to read IDs, scope, glossary, roles, functional requirements (CAT-FR-001 to 101), non-functional requirements, out of scope, decisions log
- **Part 2 — API Contract:** conventions, public, admin and internal endpoints, traceability
- **Part 3 — Data Model:** size estimate, conventions, ER diagrams, table definitions, design decisions, app-enforced rules, indexes, traceability, stock sync decision
- **Part 4 — Test Specification:** approach, fixtures, 104 test cases traced to requirement IDs, coverage summary
- **Part 5 — Implementation Plan:** way of working, definition of done, 29 tasks in 6 milestones, each mapped to its requirements and tests

---

# Part 1 — Requirements


### 0. How to read requirement IDs

Every requirement has an ID such as `CAT-FR-001`:

- **CAT** — the module (Catalog). Other modules get their own prefix, e.g. `ORD` for orders, `INV` for inventory.
- **FR** — Functional Requirement: something the system must *do*. **NFR** — Non-Functional Requirement: how *well* it must do it (speed, availability, security).
- **001** — the number. Sections are numbered in tens (001 products, 010 attributes, 020 categories …) so new requirements can be inserted without renumbering.

Tests, commits and pull requests should reference these IDs (e.g. a test named after `CAT-FR-001 AC2`) so every requirement can be traced to the code and tests that satisfy it.

Acceptance criteria (AC) are written as **Given / When / Then** and map directly to test cases.

---

### 1. Purpose and scope

The catalog module stores and serves everything a shopper needs to find and evaluate a product: what it is, which variants exist, what it costs, how much is in stock, and where it sits in the category tree. It also gives staff the tools to create, edit, publish and retire products.

The catalog **does not** process orders, reserve stock, or apply promotions. Those belong to other modules that consume catalog data.

### 2. Glossary

| Term | Meaning |
|---|---|
| **Product** | The parent listing a shopper sees, e.g. "Classic Cotton T-Shirt". It is not purchasable by itself. |
| **Variant** | One purchasable version of a product, e.g. "Classic Cotton T-Shirt, Blue, M". Every product has at least one. |
| **SKU** | Stock Keeping Unit: the unique code that identifies one variant across all systems. |
| **Option** | A dimension that distinguishes variants, e.g. Size or Color. |
| **Attribute** | A descriptive property, e.g. "Material: Cotton". Attributes don't create new variants. |
| **Attribute template** | The list of attributes a category expects, e.g. Laptops expect RAM and Screen Size. |
| **Base price** | The normal price of a variant in a given currency. |
| **Sale price** | An optional lower price that is active only between a start and end time. |
| **Effective price** | The price the shopper actually pays right now: the sale price if one is active, otherwise the base price. |
| **Slug** | The human-readable part of a URL, e.g. `classic-cotton-t-shirt`. |
| **Inventory module** | The separate module that owns stock quantities. The catalog only reads from it. |

### 3. Actors and roles

| Actor | Can do |
|---|---|
| **Shopper** (anonymous) | Read active products, categories and brands. Browse, filter, sort. |
| **Catalog Editor** | Everything a shopper can, plus create and edit products and variants, upload images, and publish and archive products. |
| **Admin** | Everything an editor can, plus manage categories, brands and attribute templates, and run bulk imports. |
| **Order / Inventory modules** (internal) | Read any product or variant by SKU, including archived ones. |

---

### 4. Functional requirements

#### 4.1 Products and variants

**CAT-FR-001 — Create product.** An editor can create a product with a name, description, brand, one primary category, optional extra categories, and at least one variant.
- AC1: *Given* a valid payload, *when* an editor creates a product, *then* it is saved in **Draft** status and a unique slug is generated from the name.
- AC2: *Given* a payload without a name or primary category, *when* submitted, *then* it is rejected with a validation error naming each missing field.

**CAT-FR-002 — Variants and options.** A product can define up to 3 options (e.g. Size, Color, Material). Each variant is one unique combination of option values. A product with no options has exactly one default variant.
- AC1: *Given* a product with options Size and Color, *when* two variants with the same Size and Color are submitted, *then* the second is rejected as a duplicate.
- AC2: *Given* a product with no options, *when* it is created, *then* it has exactly one variant.

**CAT-FR-003 — SKU uniqueness.** Every variant has a SKU that is unique across the whole catalog, including archived products, and never changes once the variant has been published.
- AC1: *Given* SKU `TS-BLU-M` already exists anywhere, *when* another variant uses it, *then* the request is rejected.
- AC2: *Given* a published variant, *when* an editor tries to change its SKU, *then* the request is rejected.

**CAT-FR-004 — Edit conflict protection.** If two editors change the same product at the same time, the second save must not silently overwrite the first.
- AC1: *Given* editor A and editor B both opened version 3 of a product, *when* A saves and then B saves, *then* B's save is rejected with a "changed by someone else" error, and B must reload.

#### 4.2 Attributes

**CAT-FR-010 — Attribute templates.** An admin can define attribute templates on a category. Each attribute has a name, a type (text, number with unit, yes/no, single choice, multiple choice), and flags for *required* and *filterable*.

**CAT-FR-011 — Inherited templates.** A category inherits its parent's attributes and can add its own. For example, "Gaming Laptops" inherits RAM from "Laptops".

**CAT-FR-012 — Attribute validation.** A product's attributes are validated against its primary category's template.
- AC1: *Given* "RAM" is required for Laptops, *when* a laptop without RAM is published, *then* publishing fails and lists the missing attribute. A draft may still be saved without it.
- AC2: *Given* RAM is a number, *when* "sixteen" is entered, *then* it is rejected.

#### 4.3 Categories and brands

**CAT-FR-020 — Category tree.** Categories form a tree up to 4 levels deep. Each has a name, a unique slug, a parent (except root categories) and a display order.

**CAT-FR-021 — Multiple categories.** A product has exactly one primary category and may belong to any number of additional categories. The primary category decides the attribute template and the breadcrumb shown to shoppers.

**CAT-FR-022 — Safe category deletion.** A category can't be deleted while it has products or child categories.
- AC1: *Given* a category with 1 product, *when* an admin deletes it, *then* the request is rejected and states how many products are attached.

**CAT-FR-023 — Brands.** An admin can create brands (name, slug, optional logo URL). Each product belongs to at most one brand. A brand with products attached can't be deleted.

#### 4.4 Pricing (USD and EUR)

**CAT-FR-030 — Dual-currency prices.** Every variant has an explicit base price in USD and in EUR. The system never converts between currencies.
- AC1: *Given* a variant with a USD price but no EUR price, *when* its product is published, *then* publishing fails.

**CAT-FR-031 — Price precision.** Prices are stored exactly, with 2 decimal places and no floating-point rounding. They must be greater than zero.

**CAT-FR-032 — Scheduled sale price.** A variant may have a sale price with a start and end time. If set, it must be set for both currencies and must be lower than the base price in each.
- AC1: *Given* a sale from 1 Dec to 7 Dec, *when* a shopper views the product on 3 Dec, *then* the effective price is the sale price, and the base price is also returned so a "was" price can be shown.
- AC2: *Given* the same sale, *when* viewed on 8 Dec, *then* the effective price is the base price. No manual action is needed.

**CAT-FR-033 — Currency selection.** Public read requests specify a currency. If none is given, EUR is used. Responses contain prices in the requested currency only.

**CAT-FR-034 — Product "from" price.** In listings, a product shows the lowest effective price among its active variants in the requested currency.

#### 4.5 Stock display

**CAT-FR-040 — Stock source.** Stock quantities are owned by the inventory module. The catalog reads them and never changes them.

**CAT-FR-041 — Shopper stock display.** Each variant shows one of three availability states:

| Quantity | Shopper sees |
|---|---|
| more than 10 | "In stock" |
| 1–10 | "Only *N* left" (exact number) |
| 0 | "Out of stock" |

- AC1: *Given* quantity 3, *when* a shopper views the variant, *then* the response says low stock with quantity 3.
- AC2: *Given* quantity 250, *when* a shopper views it, *then* the response says "In stock" without revealing 250.

**CAT-FR-042 — Staff stock display.** Editors and admins always see the exact quantity.

**CAT-FR-043 — No backorders.** A variant with zero stock is shown as unavailable, and the catalog flags it as not purchasable.

**CAT-FR-044 — Inventory unavailable.** If the inventory module can't be reached, the catalog serves the last known stock value for up to 5 minutes. After that it shows "Availability unknown", and product pages still load.

#### 4.6 Lifecycle

**CAT-FR-050 — Status flow.** Products move Draft → Active → Archived. Archived products can be restored to Draft. Nothing is ever hard-deleted.

**CAT-FR-051 — Publish validation.** A product can become Active only if it has a name, description, primary category, all required attributes, at least one variant with both prices, and at least one image.

**CAT-FR-052 — Archived product visibility.** Archived products disappear from browse and filter results. A shopper visiting an archived product's URL gets a "no longer available" response. Internal modules can still read it by SKU, so old orders keep working.

**CAT-FR-053 — Variant deactivation.** An individual variant can be deactivated (e.g. a discontinued colour) without archiving the whole product. A product with zero active variants can't be Active.

#### 4.7 Media

**CAT-FR-060 — Images.** Image files live in object storage (e.g. S3). The catalog stores each image's URL, alt text (for accessibility), and position. Exactly one image per product is primary. Images can optionally be linked to specific variants, so picking "Blue" shows blue photos.

**CAT-FR-061 — Image limits.** Allowed formats are JPEG, PNG and WebP, with a maximum of 10 MB per file and 20 images per product. Video is out of scope.

#### 4.8 URLs and slugs

**CAT-FR-070 — Unique slugs.** Product slugs are lowercase, hyphen-separated and unique across all products. If a slug is already taken, a number is appended (`t-shirt-2`).

**CAT-FR-071 — Redirects on rename.** When a product's slug changes, the old slug redirects permanently to the new one.
- AC1: *Given* a product renamed from `blue-shirt` to `navy-shirt`, *when* a shopper requests `blue-shirt`, *then* they are sent to `navy-shirt` with a permanent-redirect status.

#### 4.9 Browse, filter and sort

**CAT-FR-080 — Browse by category.** Browsing a category returns active products in that category **and all its subcategories**.

**CAT-FR-081 — Filters.** Shoppers can combine these filters: brand, price range (effective price, in the requested currency), in-stock only, and any attribute marked *filterable*.

**CAT-FR-082 — Facet counts.** Filter options show how many products match each value (e.g. "Brand: Nike (42)"), calculated with the other active filters applied.

**CAT-FR-083 — Sorting.** Sort options are newest, price low→high, price high→low, and name A→Z. Default is newest.

**CAT-FR-084 — Pagination.** Public listings use cursor-based pagination: the response includes a token for fetching the next page. Default page size is 24 and the maximum is 100. Admin listings may use numbered pages.

#### 4.10 Bulk import

**CAT-FR-090 — CSV import.** Admins can upload a CSV of up to 10,000 rows. Rows are matched by SKU: an existing SKU is updated, a new SKU is created.

**CAT-FR-091 — Import runs in the background.** The upload returns immediately with a job ID. The admin can check the job's progress and final result.

**CAT-FR-092 — Per-row results.** Valid rows are imported and invalid rows are skipped. The result report lists each failed row number and the reason.
- AC1: *Given* a 100-row file where row 37 has no EUR price, *when* the import finishes, *then* 99 rows are saved and the report shows "Row 37: EUR price missing".

**CAT-FR-093 — Imports create drafts.** Imported new products land in Draft, so nothing goes live without review.

#### 4.11 Audit

**CAT-FR-100 — Change history.** Every change to a product, variant, price, category, brand or attribute template records who made it, when, and each changed field's old and new values. This includes changes made by imports.

**CAT-FR-101 — History access.** Editors and admins can view a product's history, newest first. History records are retained for at least 2 years and can't be edited.

---

### 5. Non-functional requirements

| ID | Requirement |
|---|---|
| CAT-NFR-001 | **Read speed:** product detail and listing responses under 200 ms at the 95th percentile (95% of requests are faster than this). |
| CAT-NFR-002 | **Write speed:** single-product saves under 500 ms at the 95th percentile. |
| CAT-NFR-003 | **Freshness:** edits, price changes and stock changes are visible to shoppers within 10 seconds. Short-lived caching is allowed. |
| CAT-NFR-004 | **Availability:** public reads available 99.9% of the time (about 43 minutes of downtime a month). |
| CAT-NFR-005 | **Scale:** meets the speed targets with 10,000 products (~50,000 variants) and 50 read requests per second at peak. |
| CAT-NFR-006 | **Security:** all write operations require an authenticated user with the right role. Every write is checked on the server, never trusted from the client. |
| CAT-NFR-007 | **API:** versioned REST with JSON (e.g. `/v1/...`), one consistent error format with a machine-readable code and a human-readable message, and field-level details on validation errors. |
| CAT-NFR-008 | **Data integrity:** an Active product can never be in an invalid state (see CAT-FR-051). This is enforced on every edit, not just at publish time. |

### 6. Out of scope

Full-text search, reviews and ratings, recommendations, cart, checkout, orders, stock reservation, promotions and coupons, tax calculation, shipping, digital products, bundles, subscriptions, multiple sellers, languages other than English, product video, scheduled publishing.

### 7. Decisions log

| # | Decision | Source |
|---|---|---|
| D1 | Single seller | Product owner |
| D2 | ~5k products at launch, up to 10k | Product owner |
| D3 | USD and EUR, English only | Product owner |
| D4 | Shoppers see stock levels | Product owner |
| D5 | Explicit price per currency, no conversion | Accepted default |
| D6 | Both currencies required to publish; sale prices set in both | Accepted default |
| D7 | EUR is the default currency when none is specified | Accepted default |
| D8 | Inventory module owns stock; catalog only reads it | Accepted default |
| D9 | Exact stock shown only at 10 or below | Accepted default |
| D10 | No backorders; displayed stock may lag up to 10 s, checkout does the final check | Accepted default |
| D11 | Variants with up to 3 options; category attribute templates with inheritance | Accepted default |
| D12 | CSV import does per-row success, matches by SKU, creates drafts | Accepted default |
| D13 | Editors can publish; only admins manage categories, brands, templates and imports | Accepted default |
| D14 | Physical goods only; brands yes, tags no | Accepted default |
| D15 | Draft → Active → Archived, no hard deletes, audit history kept | Accepted default |
| D16 | Images in object storage; catalog stores ordered URLs with one primary | Accepted default |
| D17 | No full-text search in v1; browse, filter and sort only | Accepted default |
| D18 | A sale is active from its start time (inclusive) until its end time (exclusive) | Confirmed |
| D19 | Slug generation: lowercase, accents removed, anything not a letter or digit becomes a single hyphen, trimmed, max 140 characters | Confirmed |
| D20 | Drafts can't be archived directly (an abandoned draft just stays a draft); only Archived products can be restored | Confirmed |
| D21 | Facet counts for a filter ignore that filter's own selection, so shoppers still see the alternatives | Confirmed |
| D22 | An import row that would make an Active product invalid is rejected, and the product is left unchanged | Confirmed |
| D23 | After several renames, every old slug redirects straight to the current one (no redirect chains) | Confirmed |
| D24 | Missing required fields on create return `400` with field details, not `422` | Confirmed |
| D25 | Stock sync (closes Q1): inventory **pushes** absolute stock levels as events; the catalog applies an event only if it is newer than what it holds, and runs a full reconciliation every 15 minutes and at startup as a safety net | Confirmed |
| D26 | "Inventory unreachable" (CAT-FR-044) is detected by a heartbeat that inventory sends every 30 seconds, not by the age of each variant's stock value | Confirmed |
| D27 | Event transport: a RabbitMQ queue while inventory runs as a separate service; in-process Spring events if both modules are deployed as one application. Catalog logic is identical either way | Confirmed |
| D28 | Build setup: Spring Boot 4.1 (current release line), Java 21 LTS, Maven. Maven over Gradle: more common in Spring teams and its XML build file is simpler to read; Gradle builds faster but its scripted builds are harder to review | Confirmed |

---

# Part 2 — API Contract


### 1. Shared conventions

#### Versioning (CAT-NFR-007)
Every path starts with `/v1`. A breaking change means a new `/v2` path, while `/v1` keeps working until clients move over.

#### Three audiences, three path groups

| Prefix | Who calls it | Authentication |
|---|---|---|
| `/v1/...` | Shoppers (the storefront) | None needed |
| `/v1/admin/...` | Editors and Admins | Logged-in user with the right role |
| `/v1/internal/...` | Other backend modules (orders, inventory) | Service-to-service credentials, not reachable from the internet |

Splitting admin paths out lets the gateway require login for everything under `/admin` in one rule, instead of checking endpoint by endpoint.

#### How products are identified
Public endpoints use the **slug**, because that is what appears in shopper-facing URLs. Admin and internal endpoints use a permanent **ID**, because slugs change when products are renamed (CAT-FR-071).

#### Currency (CAT-FR-033)
Public read endpoints accept `currency=USD` or `currency=EUR`. If missing, EUR is used. Any other value returns `400 Bad Request`.

#### Money format (CAT-FR-031)
Amounts are sent as decimal **strings** such as `"19.99"`, always with the currency alongside. JSON numbers risk floating-point rounding in some client languages (19.99 becoming 19.989999).

#### Timestamps
ISO 8601 in UTC, e.g. `2026-12-01T00:00:00Z`.

#### Edit conflicts (CAT-FR-004)
Every product carries a `version` number. Any update to a product **or anything belonging to it** (variants, prices, images) must send the product version it was based on. If someone else saved in between, the server returns `409 Conflict` and the editor must reload.

*Trade-off:* the alternative is HTTP's `ETag` / `If-Match` headers. That is more standard, but a field in the body is easier for frontend developers to see and debug.

#### Pagination (CAT-FR-084)

| Where | Style | Request | Response |
|---|---|---|---|
| Public listings | Cursor | `cursor`, `limit` (default 24, max 100) | `items`, `nextCursor` (empty on last page) |
| Admin listings | Numbered pages | `page`, `size` | `items`, `page`, `totalCount` |

Cursor pagination doesn't skip or repeat items when products are added while a shopper scrolls. Admins want "page 7 of 12" and exact totals, which is cheap at 10k products.

#### Error format (CAT-NFR-007)

| Field | Meaning |
|---|---|
| `code` | Machine-readable, e.g. `SKU_ALREADY_EXISTS` |
| `message` | Human-readable explanation |
| `details` | List of `{field, issue}` for validation errors, e.g. `{"variants[2].prices.EUR", "required"}` |
| `traceId` | ID for finding this request in the logs |

#### Status codes

| Code | When |
|---|---|
| `200 OK` | Successful read or update |
| `201 Created` | Something new was created |
| `202 Accepted` | Background job started (bulk import) |
| `301 Moved Permanently` | Old slug; redirect to the new one (CAT-FR-071) |
| `400 Bad Request` | Malformed request: wrong types, unknown currency |
| `401 Unauthorized` | Not logged in |
| `403 Forbidden` | Logged in, but the role isn't allowed |
| `404 Not Found` | Doesn't exist (also used for Draft products on public endpoints) |
| `409 Conflict` | Edit conflict, duplicate SKU, or deleting something still in use |
| `410 Gone` | Archived product viewed by a shopper (CAT-FR-052) |
| `422 Unprocessable Entity` | Well-formed request that breaks a business rule, e.g. publishing without a EUR price |

`400` means "I can't read what you sent"; `422` means "I understood it, but it isn't allowed". This tells the frontend whether it has a bug or the user has a fixable mistake.

---

### 2. Public endpoints (Shopper)

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| P1 | `GET /v1/products` | Browse, filter, sort | 034, 041, 080, 081, 083, 084 |
| P2 | `GET /v1/products/facets` | Filter options with counts | 082 |
| P3 | `GET /v1/products/{slug}` | Product detail page | 033, 041, 052, 060, 071 |
| P4 | `GET /v1/categories` | Full category tree for navigation | 020 |
| P5 | `GET /v1/categories/{slug}` | One category plus its filterable attributes | 010, 081 |
| P6 | `GET /v1/brands` | Brand list | 023 |

#### P1 query parameters

| Parameter | Example | Notes |
|---|---|---|
| `category` | `laptops` | Includes all subcategories (CAT-FR-080) |
| `brand` | `acme,globex` | Comma means OR |
| `priceMin`, `priceMax` | `20`, `100` | Compared against the **effective** price in the requested currency |
| `inStock` | `true` | Hide out-of-stock products |
| `attr.{code}` | `attr.ram=16,32` | Only for attributes marked filterable |
| `sort` | `price_asc` | `newest` (default), `price_asc`, `price_desc`, `name_asc` |
| `currency`, `cursor`, `limit` | | See conventions |

#### P2 — why facets are a separate endpoint
P2 takes the same filters as P1 and returns counts like "Brand: Acme (42)". Counting is the expensive part of a listing request. As a separate endpoint, the storefront can load products and counts in parallel and cache them separately, and paging doesn't recompute counts.

*Trade-off:* two requests instead of one, accepted to keep P1 inside the 200 ms target (CAT-NFR-001).

#### P1 listing item fields
`slug`, `name`, `brand`, `primaryImage` (URL and alt text), `fromPrice` (lowest effective price across active variants, CAT-FR-034), `onSale` (true if any variant is on sale), `availability` (best status across its variants).

#### P3 product detail fields

| Field | Contents |
|---|---|
| `slug`, `name`, `description` | Basics |
| `brand` | Name and slug |
| `breadcrumb` | Path through the **primary** category, e.g. Electronics › Laptops › Gaming (CAT-FR-021) |
| `attributes` | Name, value, unit |
| `options` | e.g. Size: [S, M, L]; Color: [Blue, Red] |
| `images` | Ordered list: URL, alt text, `isPrimary`, linked variant IDs (CAT-FR-060) |
| `variants` | Active variants only, each with SKU, option values, `price`, `availability` |

#### Price object (CAT-FR-032)

| Field | Example | Notes |
|---|---|---|
| `currency` | `EUR` | |
| `basePrice` | `"29.99"` | Lets the storefront show a "was" price |
| `effectivePrice` | `"19.99"` | What the shopper pays now |
| `onSale` | `true` | |
| `saleEndsAt` | `2026-12-07T23:59:59Z` | Only present while on sale |

#### Availability object (CAT-FR-041, 043, 044)

| Field | Values |
|---|---|
| `status` | `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`, `UNKNOWN` |
| `quantity` | Only present when `LOW_STOCK` (1–10). Never revealed above 10 |
| `purchasable` | `false` when out of stock or unknown (no backorders) |

#### P3 special responses
- Old slug → `301` pointing to the new slug (CAT-FR-071).
- Archived product → `410 Gone` with its name, so the storefront can say "no longer available" (CAT-FR-052).
- Draft product → `404`, so shoppers don't learn unreleased products exist.

---

### 3. Admin endpoints (Editor and Admin)

#### Products and variants

| # | Method and path | Purpose | Role | Requirements |
|---|---|---|---|---|
| A1 | `POST /v1/admin/products` | Create product (starts as Draft) | Editor | 001, 002, 003, 070 |
| A2 | `GET /v1/admin/products` | List all statuses; filter by status, category, brand; search by name or SKU | Editor | 084 |
| A3 | `GET /v1/admin/products/{id}` | Full detail including exact stock and `version` | Editor | 042, 004 |
| A4 | `PUT /v1/admin/products/{id}` | Update name, description, brand, categories, attributes | Editor | 004, 012, 021, 071 |
| A5 | `POST /v1/admin/products/{id}/variants` | Add a variant | Editor | 002, 003 |
| A6 | `PUT /v1/admin/products/{id}/variants/{variantId}` | Edit variant: option values, prices, sale price | Editor | 003, 030–032 |
| A7 | `POST /v1/admin/products/{id}/variants/{variantId}/deactivate` | Deactivate one variant | Editor | 053 |
| A8 | `POST /v1/admin/products/{id}/variants/{variantId}/activate` | Reactivate it | Editor | 053 |

#### Status changes (actions, not field edits)

| # | Method and path | Transition | Role | Requirements |
|---|---|---|---|---|
| A9 | `POST /v1/admin/products/{id}/publish` | Draft → Active | Editor | 050, 051 |
| A10 | `POST /v1/admin/products/{id}/archive` | Active → Archived | Editor | 050, 052 |
| A11 | `POST /v1/admin/products/{id}/restore` | Archived → Draft | Editor | 050 |

Publishing runs the full validation in CAT-FR-051, so it gets its own endpoint rather than letting A4 set `status: ACTIVE`. This makes the validation impossible to bypass, and gives one place to return every problem at once in a `422` (e.g. "missing EUR price on variant TS-BLU-M; required attribute RAM missing; no images").

#### Images

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| A12 | `POST /v1/admin/uploads` | Get a temporary upload URL | 060, 061 |
| A13 | `POST /v1/admin/products/{id}/images` | Attach an uploaded image (URL, alt text, variant links) | 060 |
| A14 | `PUT /v1/admin/products/{id}/images/order` | Reorder images and set the primary one | 060 |
| A15 | `DELETE /v1/admin/products/{id}/images/{imageId}` | Remove an image | 060 |

Uploads use a **pre-signed URL**: a temporary, one-time link that lets the browser upload straight to object storage (e.g. S3) without permanent credentials. A12 checks file type and size (CAT-FR-061) and returns the link; the browser uploads directly; A13 records the image on the product.

*Trade-off:* routing image bytes through the Spring Boot server is simpler to build, but ties up the API with large, slow uploads for no benefit.

#### Categories, attributes, brands (Admin only)

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| A16 | `POST /v1/admin/categories` | Create category | 020 |
| A17 | `PUT /v1/admin/categories/{id}` | Rename, move, reorder | 020 |
| A18 | `DELETE /v1/admin/categories/{id}` | Delete (`409` if it has products or children) | 022 |
| A19 | `PUT /v1/admin/categories/{id}/attributes` | Set its attribute template | 010, 011 |
| A20 | `GET /v1/admin/categories/{id}/attributes` | Get the template, including inherited attributes | 011 |
| A21 | `POST` / `PUT` / `DELETE /v1/admin/brands/...` | Manage brands (`409` on delete if in use) | 023 |

#### Bulk import (Admin only)

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| A22 | `POST /v1/admin/imports` | Upload CSV; returns `202` with a `jobId` | 090, 091 |
| A23 | `GET /v1/admin/imports/{jobId}` | Status: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, plus progress | 091 |
| A24 | `GET /v1/admin/imports/{jobId}/report` | Per-row results: created, updated, failed with reason | 092 |

Validating and saving 10,000 rows can take longer than an HTTP request should stay open, hence `202 Accepted` and a job to poll.

#### History

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| A25 | `GET /v1/admin/products/{id}/history` | Changes, newest first: who, when, field, old value, new value | 100, 101 |

There is deliberately no endpoint to edit or delete history (CAT-FR-101).

---

### 4. Internal endpoints (other modules)

| # | Method and path | Purpose | Requirements |
|---|---|---|---|
| I1 | `GET /v1/internal/variants/{sku}` | One variant by SKU, **including archived**, with product name and both prices | 052 |
| I2 | `GET /v1/internal/variants?skus=A,B,C` | Up to 100 SKUs in one call | 052 |

I2 exists because checkout needs every cart item at once: one call for 20 items instead of 20 calls.

**Stock direction.** No endpoint lets anyone write stock into the catalog. The catalog *reads* quantities from the inventory module (CAT-FR-040). That call belongs to the inventory module's own contract.

---

### 5. Traceability check

| Requirements | Covered by |
|---|---|
| 001–004 | A1, A3–A6 |
| 010–012 | A4, A19, A20, P5 |
| 020–023 | A16–A18, A21, P4, P6 |
| 030–034 | A6, P1, P3 |
| 040–044 | P1, P3, A3 |
| 050–053 | A7–A11, P3 |
| 060–061 | A12–A15, P3 |
| 070–071 | A1, A4, P3 |
| 080–084 | P1, P2, A2 |
| 090–093 | A22–A24 |
| 100–101 | A25 |

---

# Part 3 — Data Model


### 1. Scope

**In this database:** everything the catalog owns: products, variants, prices, categories, brands, attributes, images, slugs, imports, audit history, plus a read-only copy of stock levels.

**Not in this database:**
- **Users and roles** live in the identity provider. Tables store the user's ID as text (`created_by`, `changed_by`).
- **Stock quantities** are owned by the inventory module. The catalog keeps a *snapshot* only, for display and for the "last known value" fallback (CAT-FR-044).
- **Image files** live in object storage. The catalog stores their location and metadata.

---

### 2. Size estimate

| Data | Rough count | Why |
|---|---|---|
| Products | 10,000 | Upper target (D2) |
| Variants | 50,000 | ~5 per product |
| Prices | 100,000 | 2 currencies per variant |
| Attribute values | 100,000 | ~10 per product |
| Images | 60,000 | ~6 per product |
| Categories | a few hundred | 4 levels max |
| Audit rows | ~200,000 per year | Grows over time; bulk imports cause spikes |

Everything except the audit log fits in a few hundred MB, small enough for MySQL to keep almost entirely in memory. **One primary database is enough; no sharding is needed.** A read replica can be added later to meet the 99.9% availability target (CAT-NFR-004); that is an infrastructure decision and doesn't change this model.

---

### 3. Conventions

| Rule | Detail |
|---|---|
| **Primary keys** | `BIGINT UNSIGNED`, auto-increment (see decision DM-1) |
| **Money** | `DECIMAL(12,2)`. Never `FLOAT` or `DOUBLE`, which can't store 19.99 exactly (CAT-FR-031) |
| **Currency codes** | `CHAR(3)` ISO codes (`EUR`, `USD`), foreign key to the `currency` table |
| **Timestamps** | `DATETIME(3)` (millisecond precision), always stored in UTC |
| **Standard columns** | Main tables have `created_at`, `updated_at`, `created_by`, `updated_by` |
| **Slugs** | Stored lowercase, so uniqueness checks can't be fooled by capital letters |
| **Deletes** | Foreign keys use `ON DELETE RESTRICT`: the database refuses to delete anything still referenced (CAT-FR-050, 022, 023) |

---

### 4. Entity-relationship diagrams

**Legend (crow's-foot notation):**

```
 ┼    exactly one            o┼   zero or one
 ┼<   one or many            o<   zero or many
 PK   primary key            FK   foreign key          UQ   unique
```

Read a line from one box to the other, e.g. `brand o┼────o< product` means *a brand has zero or many products; a product has zero or one brand*.

#### 4A. Core structure: products, brands, categories, slugs

```
┌────────────────┐            ┌─────────────────────┐   primary    ┌─────────────────────┐
│ brand          │            │ product             │   category   │ category            │
├────────────────┤            ├─────────────────────┤              ├─────────────────────┤
│ PK id          │o┼────────o<│ PK id               │>o──────────┼ │ PK id               │
│ UQ slug        │            │ FK brand_id         │              │ FK parent_id (self) │
│ UQ name        │            │ FK primary_cat_id   │              │ UQ slug             │
│    logo_url    │            │    name             │              │    path, depth      │
└────────────────┘            │    description      │              │    sort_order       │
                              │    status, version  │              └──────────┬──────────┘
                              └───┬────────────┬────┘                         ┼
                                  ┼            ┼                              │
                                  │            │                             o<
                                 ┼<            │            ┌─────────────────┴────┐
                     ┌────────────┴───────┐    └──────────o<│ product_category     │
                     │ product_slug       │                 ├──────────────────────┤
                     ├────────────────────┤                 │ PK,FK product_id     │
                     │ PK slug            │                 │ PK,FK category_id    │
                     │ FK product_id      │                 └──────────────────────┘
                     │    is_current      │
                     │ UQ current_marker  │
                     └────────────────────┘

 category.parent_id → category.id : a category has 0..N child categories (root has none)
```

#### 4B. Attributes

```
┌────────────────┐       ┌─────────────────────────┐       ┌───────────────────────────┐
│ category       │┼────o<│ attribute_definition    │┼────o<│ attribute_option          │
│ PK id          │       ├─────────────────────────┤       ├───────────────────────────┤
└────────────────┘       │ PK id                   │       │ PK id                     │
                         │ FK category_id          │       │ FK attribute_definition_id│
                         │ UQ (category_id, code)  │       │ UQ (attribute_definition_ │
                         │    label, data_type     │       │     id, value)            │
                         │    unit                 │       │    sort_order             │
                         │    is_required          │       └─────────────┬─────────────┘
                         │    is_filterable        │                    o┼
                         └────────────┬────────────┘                     │
                                      ┼                                  │
                                      │                                  │
                                     o<                                 o<
┌────────────────┐       ┌────────────┴──────────────────────────────────┴────┐
│ product        │┼────o<│ product_attribute_value                            │
│ PK id          │       ├────────────────────────────────────────────────────┤
└────────────────┘       │ PK id                                              │
                         │ FK product_id, FK attribute_definition_id          │
                         │ FK attribute_option_id   (choice types only)       │
                         │    value_text | value_number | value_boolean       │
                         │ UQ (product_id, attribute_definition_id,           │
                         │     option_key)                                    │
                         └────────────────────────────────────────────────────┘
```

#### 4C. Variants, options, prices, stock

```
┌────────────────┐      ┌──────────────────────┐      ┌─────────────────────────┐
│ product        │┼───o<│ product_option       │┼───┼<│ product_option_value    │
│ PK id          │      ├──────────────────────┤      ├─────────────────────────┤
└───────┬────────┘      │ PK id                │      │ PK id                   │
        ┼               │ FK product_id        │      │ FK product_option_id    │
        │               │    name              │      │    value, position      │
        │               │ UQ (product_id,      │      │ UQ (product_option_id,  │
        │               │     position 1..3)   │      │     value)              │
       ┼<               └──────────────────────┘      └────────────┬────────────┘
┌───────┴──────────────────────┐                                   ┼
│ variant                      │                                   │
├──────────────────────────────┤                                  o<
│ PK id                        │      ┌────────────────────────────┴───┐
│ FK product_id                │┼───┼<│ variant_option_value           │
│ UQ sku                       │      ├────────────────────────────────┤
│ UQ (product_id,              │      │ PK,FK variant_id               │
│     option_signature)        │      │ PK,FK product_option_id        │
│    is_active, position       │      │ FK product_option_value_id     │
│    sale_starts_at            │      └────────────────────────────────┘
│    sale_ends_at              │
└───────┬───────────────┬──────┘
        ┼               ┼
        │               │
       ┼<              o┼
┌───────┴────────────┐ ┌┴───────────────────────┐   ┌─────────────────────┐
│ variant_price      │ │ variant_stock_snapshot │   │ currency            │
├────────────────────┤ ├────────────────────────┤   ├─────────────────────┤
│ PK,FK variant_id   │ │ PK,FK variant_id       │   │ PK code             │
│ PK,FK currency_code│>o──────────────────────────┼ │    name             │
│    base_amount     │ │    quantity            │   │    is_enabled       │
│    sale_amount     │ │    source_updated_at   │   └─────────────────────┘
└────────────────────┘ │    fetched_at          │
                       └────────────────────────┘

 variant_price.currency_code → currency.code : each price row is in exactly one currency
```

A product with no options has no `product_option` rows and one variant with `option_signature = 'default'` (CAT-FR-002 AC2). Variants of a product with options therefore have one `variant_option_value` row per option; a product without options has none.

#### 4D. Images

```
┌─────────────────────┐        ┌──────────────────────────┐        ┌──────────────┐
│ product             │┼─────o<│ product_image            │        │ variant      │
│ PK id               │        ├──────────────────────────┤        │ PK id        │
└─────────────────────┘        │ PK id                    │        └──────┬───────┘
                               │ FK product_id            │               ┼
                               │    storage_key, url      │               │
                               │    alt_text              │               │
                               │    content_type          │               │
                               │    size_bytes            │               │
                               │ UQ (product_id, position)│               │
                               │ UQ primary_marker        │               │
                               └────────────┬─────────────┘               │
                                            ┼                             │
                                            │                             │
                                           o<                            o<
                               ┌────────────┴─────────────────────────────┴───┐
                               │ product_image_variant                        │
                               ├──────────────────────────────────────────────┤
                               │ PK,FK image_id                               │
                               │ PK,FK variant_id                             │
                               └──────────────────────────────────────────────┘
```

#### 4E. Operations: imports, audit, listing read model

```
┌────────────────────┐        ┌─────────────────────────┐
│ import_job         │┼─────o<│ import_job_error        │
├────────────────────┤        ├─────────────────────────┤
│ PK id              │        │ PK id                   │
│    status          │        │ FK import_job_id        │
│    file_storage_key│        │    row_number, sku      │
│    total_rows      │        │    field, error_code    │
│    processed_rows  │        │    message              │
│    *_count         │        └─────────────────────────┘
└────────────────────┘

┌────────────────────────────┐          ┌──────────────────────────────┐
│ audit_log                  │          │ product_listing (read model) │
├────────────────────────────┤          ├──────────────────────────────┤
│ PK id                      │          │ PK,FK product_id             │
│    change_id               │          │ PK,FK currency_code          │
│    product_id  (no FK)     │          │    slug, name, brand_id      │
│    entity_type, entity_id  │          │    primary_image_url         │
│    action, field_name      │          │    from_price, on_sale       │
│    old_value, new_value    │          │    availability              │
│    changed_by, source      │          │    published_at              │
│    changed_at              │          └──────────────────────────────┘
└────────────────────────────┘

 audit_log has no foreign keys on purpose (decision DM-11).
 product_listing is derived data, rebuilt from the tables above (decision DM-9).
```

---

### 5. Table definitions

"Null" = whether the column may be empty.

#### 5.1 `brand` — CAT-FR-023

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| name | VARCHAR(120) | No | UQ |
| slug | VARCHAR(140) | No | UQ |
| logo_url | VARCHAR(1024) | Yes | |
| created_at, updated_at, created_by, updated_by | | No | Standard columns |

#### 5.2 `category` — CAT-FR-020, 022

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| parent_id | BIGINT UNSIGNED | Yes | FK → category.id. Empty for root categories |
| name | VARCHAR(120) | No | |
| slug | VARCHAR(140) | No | UQ |
| path | VARCHAR(255) | No | Ancestor IDs including itself, e.g. `/1/5/12/` (decision DM-2) |
| depth | TINYINT | No | CHECK 1–4 |
| sort_order | INT | No | Display order among siblings |
| standard columns | | No | |

Indexes: `(parent_id, sort_order)` for building the tree; `(path)` for "this category and everything below it".

#### 5.3 `attribute_definition` — CAT-FR-010, 011

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| category_id | BIGINT UNSIGNED | No | FK → category.id |
| code | VARCHAR(64) | No | Machine name, e.g. `ram`. Used in filter URLs (`attr.ram`) |
| label | VARCHAR(120) | No | Display name, e.g. "RAM" |
| data_type | ENUM | No | `TEXT`, `NUMBER`, `BOOLEAN`, `SINGLE_SELECT`, `MULTI_SELECT` |
| unit | VARCHAR(20) | Yes | Only for `NUMBER`, e.g. "GB" |
| is_required | BOOLEAN | No | Checked at publish time |
| is_filterable | BOOLEAN | No | Allowed as a shopper filter |
| sort_order | INT | No | |
| standard columns | | No | |

Unique: `(category_id, code)`.

#### 5.4 `attribute_option` — choices for select-type attributes

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| attribute_definition_id | BIGINT UNSIGNED | No | FK |
| value | VARCHAR(120) | No | e.g. "16" for RAM, "Cotton" for Material |
| sort_order | INT | No | |

Unique: `(attribute_definition_id, value)`.

#### 5.5 `product` — CAT-FR-001, 004, 021, 050

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| name | VARCHAR(200) | No | |
| description | TEXT | Yes | Required only to publish (CAT-FR-051) |
| brand_id | BIGINT UNSIGNED | Yes | FK → brand.id |
| primary_category_id | BIGINT UNSIGNED | No | FK → category.id |
| status | ENUM | No | `DRAFT` (default), `ACTIVE`, `ARCHIVED` |
| version | INT UNSIGNED | No | Starts at 1; increases on every change to the product or its children (DM-12) |
| published_at | DATETIME(3) | Yes | Set when first made Active; drives "newest" sort |
| archived_at | DATETIME(3) | Yes | |
| standard columns | | No | |

Indexes: `(status, published_at)`, `(brand_id)`, `(primary_category_id)`.

The product's slug is **not** a column here; it lives in `product_slug` (DM-8).

#### 5.6 `product_slug` — CAT-FR-070, 071

| Column | Type | Null | Notes |
|---|---|---|---|
| slug | VARCHAR(140) | No | PK: one slug, ever, across all products |
| product_id | BIGINT UNSIGNED | No | FK → product.id |
| is_current | BOOLEAN | No | True for the live slug; false for old ones that redirect |
| current_marker | BIGINT UNSIGNED | Yes | **Generated**: equals `product_id` when `is_current` is true, otherwise empty. UQ (DM-7) |
| created_at | DATETIME(3) | No | |

Index: `(product_id)`.

#### 5.7 `product_category` — CAT-FR-021, 080

| Column | Type | Null | Notes |
|---|---|---|---|
| product_id | BIGINT UNSIGNED | No | PK part, FK |
| category_id | BIGINT UNSIGNED | No | PK part, FK |

Index: `(category_id, product_id)` for browsing. **The primary category also has a row here**, so browse queries only need this one table.

#### 5.8 `product_attribute_value` — CAT-FR-012, 081

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| product_id | BIGINT UNSIGNED | No | FK |
| attribute_definition_id | BIGINT UNSIGNED | No | FK |
| attribute_option_id | BIGINT UNSIGNED | Yes | FK. Used for `SINGLE_SELECT` / `MULTI_SELECT` |
| value_text | VARCHAR(500) | Yes | For `TEXT` |
| value_number | DECIMAL(18,4) | Yes | For `NUMBER` |
| value_boolean | BOOLEAN | Yes | For `BOOLEAN` |
| option_key | BIGINT UNSIGNED | No | **Generated**: `attribute_option_id`, or 0 when empty |

- CHECK: exactly one of `attribute_option_id`, `value_text`, `value_number`, `value_boolean` is filled.
- Unique: `(product_id, attribute_definition_id, option_key)`. Non-choice attributes get one row per product; multi-select gets one row per chosen option.
- Indexes for filtering: `(attribute_definition_id, attribute_option_id, product_id)`, `(attribute_definition_id, value_number, product_id)`, `(attribute_definition_id, value_boolean, product_id)`.

#### 5.9 `product_option` and `product_option_value` — CAT-FR-002

**`product_option`**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| product_id | BIGINT UNSIGNED | No | FK |
| name | VARCHAR(60) | No | e.g. "Size" |
| position | TINYINT | No | CHECK 1–3 (max 3 options) |

Unique: `(product_id, position)`, `(product_id, name)`.

**`product_option_value`**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| product_option_id | BIGINT UNSIGNED | No | FK |
| value | VARCHAR(60) | No | e.g. "M" |
| position | SMALLINT | No | Display order |

Unique: `(product_option_id, value)`.

#### 5.10 `variant` — CAT-FR-002, 003, 032, 053

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| product_id | BIGINT UNSIGNED | No | FK |
| sku | VARCHAR(64) | No | UQ across the whole table, archived included (CAT-FR-003) |
| option_signature | VARCHAR(255) | No | Sorted option-value IDs joined, e.g. `14-27`; `default` if no options (DM-6) |
| is_active | BOOLEAN | No | CAT-FR-053 |
| position | SMALLINT | No | Display order |
| sale_starts_at | DATETIME(3) | Yes | One sale window shared by both currencies (DM-5) |
| sale_ends_at | DATETIME(3) | Yes | CHECK later than `sale_starts_at` |
| standard columns | | No | |

Unique: `sku`; `(product_id, option_signature)`.
Indexes: `(sale_starts_at)`, `(sale_ends_at)` so the scheduler can find sales starting or ending (DM-9).

#### 5.11 `variant_option_value` — CAT-FR-002

| Column | Type | Null | Notes |
|---|---|---|---|
| variant_id | BIGINT UNSIGNED | No | PK part, FK |
| product_option_id | BIGINT UNSIGNED | No | PK part, FK. Makes "one value per option per variant" a database rule |
| product_option_value_id | BIGINT UNSIGNED | No | FK |

Index: `(product_option_value_id)`.

#### 5.12 `currency` — CAT-FR-030, 033

| Column | Type | Null | Notes |
|---|---|---|---|
| code | CHAR(3) | No | PK. Rows: `EUR`, `USD` |
| name | VARCHAR(40) | No | |
| is_enabled | BOOLEAN | No | |

#### 5.13 `variant_price` — CAT-FR-030, 031, 032

| Column | Type | Null | Notes |
|---|---|---|---|
| variant_id | BIGINT UNSIGNED | No | PK part, FK |
| currency_code | CHAR(3) | No | PK part, FK → currency.code |
| base_amount | DECIMAL(12,2) | No | CHECK greater than 0 |
| sale_amount | DECIMAL(12,2) | Yes | CHECK empty, or greater than 0 and less than `base_amount` |
| updated_at, updated_by | | No | |

#### 5.14 `variant_stock_snapshot` — CAT-FR-040, 041, 044

| Column | Type | Null | Notes |
|---|---|---|---|
| variant_id | BIGINT UNSIGNED | No | PK, FK |
| quantity | INT | No | As reported by inventory |
| source_updated_at | DATETIME(3) | No | When inventory says it changed |
| fetched_at | DATETIME(3) | No | When the catalog last applied an event or reconciliation for this variant. Used for troubleshooting only; not for the `UNKNOWN` rule (D26) |

The catalog writes this table only from inventory data. It never originates a stock number. An incoming value is applied only if its `source_updated_at` is later than the stored one, so late or repeated events can't overwrite newer data (D25).

#### 5.14a `inventory_sync_status` — CAT-FR-044

A single-row table describing the health of the link to inventory.

| Column | Type | Null | Notes |
|---|---|---|---|
| id | TINYINT | No | PK, always 1 |
| last_heartbeat_at | DATETIME(3) | Yes | Last heartbeat received. More than 5 minutes ago → every variant shows `UNKNOWN` |
| last_reconciled_at | DATETIME(3) | Yes | Last successful full reconciliation |
| last_reconcile_corrections | INT | Yes | How many variants the last reconciliation had to fix. Rising numbers mean events are being lost |

#### 5.15 `product_image` and `product_image_variant` — CAT-FR-060, 061

**`product_image`**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| product_id | BIGINT UNSIGNED | No | FK |
| storage_key | VARCHAR(512) | No | Object storage location |
| url | VARCHAR(1024) | No | Public URL |
| alt_text | VARCHAR(250) | No | Accessibility text |
| content_type | ENUM | No | `image/jpeg`, `image/png`, `image/webp` |
| size_bytes | INT UNSIGNED | No | CHECK ≤ 10 MB |
| position | SMALLINT | No | |
| is_primary | BOOLEAN | No | |
| primary_marker | BIGINT UNSIGNED | Yes | **Generated**: `product_id` when primary, otherwise empty. UQ (DM-7) |
| standard columns | | No | |

Unique: `(product_id, position)`, `primary_marker`.

**`product_image_variant`**: `image_id` + `variant_id`, both PK parts and FKs.

Removing an image (A15) deletes its row; the removal is recorded in `audit_log`. This is the one deliberate hard delete.

#### 5.16 `product_listing` — read model for CAT-FR-034, 080–084

| Column | Type | Null | Notes |
|---|---|---|---|
| product_id | BIGINT UNSIGNED | No | PK part, FK |
| currency_code | CHAR(3) | No | PK part, FK |
| slug, name | | No | Copied from source tables |
| brand_id | BIGINT UNSIGNED | Yes | For brand filtering |
| primary_image_url, primary_image_alt | | Yes | |
| from_price | DECIMAL(12,2) | No | Lowest **effective** price across active variants, in this currency |
| on_sale | BOOLEAN | No | |
| availability | ENUM | No | Best status across variants: `IN_STOCK`, `LOW_STOCK`, `OUT_OF_STOCK`, `UNKNOWN` |
| published_at | DATETIME(3) | No | |
| refreshed_at | DATETIME(3) | No | |

Contains **only Active products**, one row per currency.
Indexes: `(currency_code, published_at, product_id)`, `(currency_code, from_price, product_id)`, `(currency_code, name, product_id)`, `(currency_code, brand_id)`.

#### 5.17 `import_job` and `import_job_error` — CAT-FR-090 to 093

**`import_job`**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK (the `jobId`) |
| status | ENUM | No | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` |
| file_storage_key | VARCHAR(512) | No | Uploaded CSV in object storage |
| total_rows, processed_rows | INT | No | Progress |
| created_count, updated_count, failed_count | INT | No | |
| failure_reason | VARCHAR(500) | Yes | Whole-job failure only (e.g. unreadable file) |
| created_by, created_at | | No | |
| started_at, finished_at | DATETIME(3) | Yes | |

Index: `(status, created_at)` so the worker can pick the oldest queued job.

**`import_job_error`**

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| import_job_id | BIGINT UNSIGNED | No | FK |
| row_number | INT | No | Row in the CSV |
| sku | VARCHAR(64) | Yes | If readable |
| field | VARCHAR(100) | Yes | |
| error_code | VARCHAR(64) | No | e.g. `PRICE_EUR_MISSING` |
| message | VARCHAR(500) | No | |

Index: `(import_job_id, row_number)`.

#### 5.18 `audit_log` — CAT-FR-100, 101

| Column | Type | Null | Notes |
|---|---|---|---|
| id | BIGINT UNSIGNED | No | PK |
| change_id | CHAR(36) | No | Groups all fields changed in one save |
| product_id | BIGINT UNSIGNED | Yes | The product this change belongs to, even for variant, price or image changes. **No FK** (DM-11) |
| entity_type | ENUM | No | `PRODUCT`, `VARIANT`, `VARIANT_PRICE`, `PRODUCT_IMAGE`, `PRODUCT_SLUG`, `CATEGORY`, `BRAND`, `ATTRIBUTE_DEFINITION` |
| entity_id | VARCHAR(64) | No | ID of the changed row |
| action | ENUM | No | `CREATE`, `UPDATE`, `STATUS_CHANGE`, `DELETE` |
| field_name | VARCHAR(100) | Yes | Empty for whole-row actions |
| old_value, new_value | TEXT | Yes | |
| changed_by | VARCHAR(64) | No | User ID |
| source | ENUM | No | `ADMIN_API`, `IMPORT` |
| import_job_id | BIGINT UNSIGNED | Yes | When `source = IMPORT` |
| changed_at | DATETIME(3) | No | |

Indexes: `(product_id, changed_at, id)` for a product's history, newest first; `(entity_type, entity_id, changed_at)` for category, brand and template history.

---

### 6. Design decisions and trade-offs

**DM-1 — Auto-increment IDs instead of UUIDs.**
MySQL's InnoDB stores each table sorted by primary key. Increasing numbers are always added at the end, which keeps tables compact and inserts fast, and they take 8 bytes. Random UUIDs take 16 bytes and land all over the table, slowing inserts. The downside of numbers is that they are guessable and reveal counts; that is acceptable because shoppers only ever see slugs, and admin IDs sit behind login.

**DM-2 — Category tree as a parent pointer plus a stored path.**
Browsing needs "this category and everything beneath it" (CAT-FR-080). Storing each category's ancestor path (`/1/5/12/`) turns that into a single indexed prefix match. The main alternative, a *closure table* (a separate table holding every ancestor–descendant pair), is more flexible but is one more table to keep consistent. With at most 4 levels and a few hundred categories, the path is simpler. Its cost is that moving a category rewrites the path of its subtree, which is rare and small.

**DM-3 — Attribute values as rows (EAV) instead of a JSON column.**
EAV (entity–attribute–value) means one row per attribute value, with a typed column for each kind of value. The alternative is one JSON column on `product`. JSON is simpler to read, but filtering on "RAM = 16" efficiently needs a dedicated index per attribute, which means a schema change every time an admin adds a filterable attribute. With EAV, the same three indexes serve every attribute, including ones created tomorrow. The cost is more rows and more joins to build a product page, which is offset by caching.

**DM-4 — One price row per currency instead of `price_eur` / `price_usd` columns.**
Adding a third currency later means inserting data, not altering tables, which satisfies "don't block more currencies later." The cost is two rows per variant instead of one, which is negligible.

**DM-5 — Sale window on the variant; sale amounts per currency.**
CAT-FR-032 requires a sale to cover both currencies. One shared start and end time makes it impossible for the EUR sale and USD sale to drift onto different dates.

**DM-6 — `option_signature` to prevent duplicate variants.**
A relational database can't directly say "no two variants of this product may have the same *set* of option values." The signature turns the set into one string (value IDs sorted, so Blue+M and M+Blue are identical), and a normal unique index then enforces the rule (CAT-FR-002 AC1). The application must set it correctly on every variant save.

**DM-7 — "At most one" rules through a generated marker column.**
Some databases (e.g. PostgreSQL) support a *partial* unique index: "unique, but only where `is_primary` is true." MySQL doesn't. The workaround: a generated column that holds `product_id` when the flag is true and is empty otherwise. Unique indexes ignore empty values, so at most one row per product can be marked. Used for the primary image and the current slug. The "at least one" half is checked by the application at publish time.

**DM-8 — Slugs in their own table.**
Current and old slugs share one uniqueness space, which stops a new product from taking a slug that still redirects to a different product. With a `slug` column on `product` plus a separate history table, the database couldn't enforce uniqueness across both.

**DM-9 — A precomputed listing table.**
Price filtering and sorting use the *effective* price, which depends on the current time and is the minimum across variants. Computing that for every request is heavier, and cursor pagination needs a stored, stable sort key (e.g. `from_price` plus `product_id`). The `product_listing` table holds those values ready-made, so a listing is one indexed read.
*Cost:* the table must be kept in sync. It is refreshed for a product in the same transaction as any change to that product, its variants, prices or images; when a stock snapshot changes; and by a scheduler that runs every 5 seconds to refresh products whose sales have just started or ended (well within the 10-second freshness target, CAT-NFR-003).
*Alternative:* compute at query time. At 10k products this would likely work too, with fewer moving parts. The read model was chosen mainly because it makes cursor pagination on price clean.

**DM-10 — No hard deletes.**
Foreign keys refuse deletion of anything still referenced, and products are archived rather than deleted, so old orders always resolve (CAT-FR-050, 052). The one exception is image removal (5.15), which is audited.

**DM-11 — Append-only audit log without foreign keys.**
The application's database user gets only insert and read permission on `audit_log`, so history can't be edited even by a bug (CAT-FR-101). It has no foreign keys, so audit rows never block other operations and survive whatever happens to the rows they describe. `product_id` is copied onto every row so a product's full history, including variants and prices, is one indexed query.

**DM-12 — One version number per product.**
The API checks conflicts at product level (CAT-FR-004), so `product.version` increases whenever the product *or anything under it* changes (variants, prices, options, attributes, images). A save only succeeds if the version it was based on is still current. Spring's JPA layer supports this pattern ("optimistic locking") out of the box.

---

### 7. Rules the database can't enforce (application responsibility)

| Rule | Requirement |
|---|---|
| Publish validation: description, required attributes, ≥1 active variant, both prices on every active variant, ≥1 image, exactly one primary image | CAT-FR-051, 030 |
| Active products stay valid after every edit, not just at publish | CAT-NFR-008 |
| SKU can't change after the variant is first published | CAT-FR-003 |
| Every variant has exactly one value for each of the product's options, and `option_signature` matches them | CAT-FR-002 |
| A sale, if set, has amounts in **both** currencies | CAT-FR-032 |
| A category can't redefine an attribute code it inherits; depth and `path` stay consistent when categories move | CAT-FR-011, 020 |
| Attribute values match their definition's data type; single-select has one row | CAT-FR-012 |
| At most 20 images per product | CAT-FR-061 |
| Every product has exactly one current slug; renaming inserts a new current slug and marks the old one as not current | CAT-FR-070, 071 |
| `product_category` includes the primary category | CAT-FR-021 |
| `product.version` bumps on any child change | CAT-FR-004 |
| `product_listing` refreshed on every relevant change | CAT-NFR-003 |
| Every write produces `audit_log` rows in the same transaction | CAT-FR-100 |

---

### 8. Key queries and the indexes behind them

| Query | Tables used | Index |
|---|---|---|
| P3 product by slug (incl. redirect) | product_slug → product | `product_slug` PK |
| P1 browse category, newest first | category → product_category → product_listing | `category(path)`, `product_category(category_id, product_id)`, `product_listing` PK |
| P1 sort by price | product_listing | `(currency_code, from_price, product_id)` |
| P1 filter by attribute | product_attribute_value | `(attribute_definition_id, attribute_option_id, product_id)` or the number/boolean equivalents |
| P1 filter by brand | product_listing | `(currency_code, brand_id)` |
| I1/I2 variant by SKU | variant → variant_price | `variant(sku)`, `variant_price` PK |
| A25 product history | audit_log | `(product_id, changed_at, id)` |
| Sale start/end scheduler | variant | `(sale_starts_at)`, `(sale_ends_at)` |
| Import worker picks next job | import_job | `(status, created_at)` |

---

### 9. Traceability

| Table | Requirements |
|---|---|
| brand | 023 |
| category | 020, 022, 080 |
| attribute_definition, attribute_option | 010, 011, 012 |
| product | 001, 004, 021, 050, 051 |
| product_slug | 070, 071 |
| product_category | 021, 080 |
| product_attribute_value | 012, 081 |
| product_option, product_option_value, variant_option_value | 002 |
| variant | 002, 003, 032, 053 |
| currency, variant_price | 030, 031, 032, 033 |
| variant_stock_snapshot | 040, 041, 042, 043, 044 |
| inventory_sync_status | 044 |
| product_image, product_image_variant | 060, 061 |
| product_listing | 034, 080, 081, 083, 084 |
| import_job, import_job_error | 090, 091, 092, 093 |
| audit_log | 100, 101 |

---

### 10. Stock sync decision (formerly open question Q1)

**Decision:** the inventory module **pushes** stock changes; the catalog doesn't poll for every change (D25–D27).

| Aspect | Decision | Why |
|---|---|---|
| Direction | Inventory publishes a "stock changed" event whenever a quantity changes | Shoppers see changes within about a second, well inside the 10-second target (CAT-NFR-003). Polling 50,000 variants every few seconds would be wasteful and still slower |
| Event content | SKU, the **absolute** quantity (not "+3" or "−1"), and inventory's change timestamp | Applying the same absolute event twice is harmless, so redelivered messages can't corrupt stock. Deltas would double-count |
| Ordering | Apply only if the event's timestamp is newer than the stored `source_updated_at` | Messages can arrive out of order; older ones are ignored |
| Safety net | Full reconciliation every 15 minutes and at startup: fetch all quantities in bulk and fix differences | Any lost event is corrected within 15 minutes, and a fresh deployment starts with correct data. This is the only polling, and it's rare |
| Outage detection | Inventory sends a heartbeat every 30 seconds; no heartbeat for 5 minutes → `UNKNOWN` everywhere (CAT-FR-044) | Under push, a variant whose stock hasn't changed for days has an "old" value that is still correct, so the age of the value can't signal an outage. The heartbeat can |
| Transport | RabbitMQ while inventory is a separate service; in-process Spring events if both run in one application | RabbitMQ is simpler to run than Kafka at this volume. Kafka's strengths, replaying history and very high throughput, aren't needed because reconciliation already covers recovery |

The inventory side (publishing events reliably, the heartbeat, the bulk quantities endpoint) belongs in the inventory module's own spec.

---

# Part 4 — Test Specification

## 1. Approach

Every acceptance criterion in Part 1 becomes at least one test. Rules without a written AC (limits, boundaries, permissions) get tests too, because an untested rule is an unverified one.

### Test IDs
Format: `TC-CAT-{requirement number}-{nn}`, e.g. `TC-CAT-001-02` is the second test for CAT-FR-001. Non-functional tests use `TC-CAT-NFR-{number}-{nn}`. **Each test's name in the codebase includes its ID**, so a build check can compare the requirement IDs in Part 1 with the test IDs in the code and fail if any requirement has no test.

### Test levels

| Code | Level | What it exercises | Runs |
|---|---|---|---|
| **U** | Unit | Pure logic with no database or HTTP, e.g. slug generation, effective-price calculation | Every build, milliseconds |
| **I** | Integration | Service layer against a **real MySQL** database | Every build, seconds |
| **A** | API | Full HTTP request through controllers, validation and security, down to the database | Every build |
| **T** | Time-based | Behaviour that depends on the clock or on background jobs | Every build, with a controllable clock |
| **P** | Performance | Speed and load targets on a production-sized dataset | Nightly or before release |

**Real MySQL, not an in-memory substitute.** Integration tests start a real MySQL 8 instance in Docker using Testcontainers (a library that manages throwaway databases for tests). The common alternative, the H2 in-memory database, is faster to start, but it doesn't behave like MySQL for things this design depends on: `CHECK` constraints, generated columns, unique indexes that ignore empty values, and collation rules. A test passing on H2 could fail in production.

**A controllable clock.** Code that asks "what time is it?" (sales, stock freshness) gets the time from an injected clock instead of the system clock. Tests set the clock to "3 December" directly rather than waiting, so time-based tests are instant and repeatable.

**A fake inventory module.** Tests replace the inventory module with a stub that returns whatever quantity the test sets, or simulates being unreachable.

**Independent tests.** Each test sets up its own data from the fixtures below and doesn't rely on what another test left behind, so tests can run in any order.

## 2. Standard fixtures

Tests refer to these by name instead of repeating setup.

| Fixture | Contents |
|---|---|
| **F-TREE** | Categories: Electronics › Laptops › Gaming Laptops; Clothing › T-Shirts |
| **F-TEMPLATE** | Laptops template: `ram` (number, GB, required, filterable), `material` (single select: Aluminium, Plastic; filterable), `warranty` (text, not filterable). Gaming Laptops adds `gpu` (text) |
| **F-BRANDS** | Acme, Globex |
| **F-TEE** | "Classic Tee", brand Acme, primary category T-Shirts. Options Size [S, M] and Color [Blue, Red]. Four variants `TEE-S-BLU`, `TEE-S-RED`, `TEE-M-BLU`, `TEE-M-RED`, each EUR 19.99 / USD 21.99. One primary image. Status Active |
| **F-LAPTOP** | "Pro Laptop 14", brand Globex, primary category Gaming Laptops, ram 16, material Aluminium. One default variant `LAP-14`, EUR 999.00 / USD 1,099.00. Active |
| **F-USERS** | `anon` (no login), `editor` (Catalog Editor), `admin` (Admin), `svc-orders` (internal service credentials) |
| **F-STOCK** | Fake inventory: every variant at quantity 50, heartbeat current, unless the test says otherwise. Tests can send stock events and heartbeats directly |
| **F-CLOCK** | Clock fixed at 2026-12-03T12:00:00Z unless the test says otherwise |
| **F-BULK** | Generated dataset for performance: 10,000 products, ~50,000 variants, realistic category and attribute spread |

## 3. Functional test cases

"Given" setup beyond the named fixtures is written in the scenario.

### 3.1 Products and variants

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-001-01 | FR-001 AC1 | I | Editor creates "Classic Tee" with valid data | Saved as `DRAFT`, slug `classic-tee`, version 1 |
| TC-CAT-001-02 | FR-001 AC2 | A | Create with no name and no primary category | `400`; `details` names both fields; nothing saved |
| TC-CAT-001-03 | FR-001 | A | Anonymous shopper requests the new draft's slug | `404` |
| TC-CAT-002-01 | FR-002 AC1 | I | F-TEE; add a second variant with Size M, Color Blue | `409 DUPLICATE_VARIANT` |
| TC-CAT-002-02 | FR-002 AC2 | I | Create a product with no options | Exactly one variant exists |
| TC-CAT-002-03 | FR-002 | U | Build the signature for (Blue, M) and for (M, Blue) | Identical signatures |
| TC-CAT-002-04 | FR-002 | A | Create a product with 4 options | `422`, max 3 options |
| TC-CAT-002-05 | FR-002 | I | F-TEE; add a variant with Size only, no Color | `422`, missing value for Color |
| TC-CAT-003-01 | FR-003 AC1 | I | Archive F-TEE; create another product using SKU `TEE-S-BLU` | `409 SKU_ALREADY_EXISTS` |
| TC-CAT-003-02 | FR-003 AC2 | I | F-TEE (published); change `TEE-S-BLU` to `TEE-S-NAVY` | `422`; SKU unchanged |
| TC-CAT-003-03 | FR-003 | I | Change a SKU on a draft that has never been published | Allowed |
| TC-CAT-004-01 | FR-004 AC1 | I | Editors A and B load version 3; A saves, then B saves | A succeeds (now version 4); B gets `409`; A's change is intact |
| TC-CAT-004-02 | FR-004 | I | Load product at version 3; someone edits a variant **price**; save the product with version 3 | `409` (child changes bump the product version) |

### 3.2 Attributes

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-010-01 | FR-010 | I | Admin creates a template containing one attribute of each of the 5 types | All saved with their flags |
| TC-CAT-010-02 | FR-010 | A | Editor tries to change a template | `403` |
| TC-CAT-010-03 | FR-010 | I | Add a second `ram` attribute to Laptops | `409` |
| TC-CAT-011-01 | FR-011 | I | Read the Gaming Laptops template | Contains inherited `ram`, `material`, `warranty` plus its own `gpu` |
| TC-CAT-011-02 | FR-011 | I | Define `ram` directly on Gaming Laptops | `422`, cannot redefine an inherited attribute |
| TC-CAT-012-01 | FR-012 AC1 | I | New laptop without `ram`: save as draft, then publish | Draft save succeeds; publish returns `422` listing `ram` |
| TC-CAT-012-02 | FR-012 AC2 | A | Set `ram` to "sixteen" | `400`; detail on the `ram` field |
| TC-CAT-012-03 | FR-012 | I | Give `material` two values; separately, give it a value from another attribute's options | Both `422` |

### 3.3 Categories and brands

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-020-01 | FR-020 | I | Add a 4th-level category under Gaming Laptops, then a 5th-level one under it | 4th succeeds; 5th returns `422` |
| TC-CAT-020-02 | FR-020 | I | Create a category with an existing slug | `409` |
| TC-CAT-020-03 | FR-020 | I | Move Gaming Laptops under Clothing | Its path and its children's paths update; browsing Laptops no longer includes F-LAPTOP |
| TC-CAT-021-01 | FR-021 | I | F-TEE also added to Electronics | Appears when browsing T-Shirts and Electronics; breadcrumb still Clothing › T-Shirts |
| TC-CAT-021-02 | FR-021 | I | Change F-TEE's primary category | Membership list includes the new primary; breadcrumb and template follow it |
| TC-CAT-022-01 | FR-022 AC1 | I | Delete T-Shirts (contains F-TEE) | `409`; message says 1 product attached |
| TC-CAT-022-02 | FR-022 | I | Delete Laptops (has a child category) | `409` |
| TC-CAT-022-03 | FR-022 | I | Delete an empty leaf category | Succeeds |
| TC-CAT-023-01 | FR-023 | I | Delete Acme (used by F-TEE); create a product with no brand | Delete `409`; brandless product saves |

### 3.4 Pricing

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-030-01 | FR-030 AC1 | I | Draft variant with a USD price but no EUR price; publish | `422` naming the variant and EUR |
| TC-CAT-030-02 | FR-030 | I | Change F-TEE's EUR price to 17.99 | USD stays 21.99 (no conversion) |
| TC-CAT-031-01 | FR-031 | A | Save 19.99, then read it back | Returned as the string `"19.99"` exactly |
| TC-CAT-031-02 | FR-031 | A | Save prices 0, -5.00 and 19.999 | All rejected; nothing saved |
| TC-CAT-032-01 | FR-032 AC1 | T | Sale 1–7 Dec at EUR 14.99; clock 3 Dec | Effective 14.99, base 19.99, `onSale` true, `saleEndsAt` present |
| TC-CAT-032-02 | FR-032 AC2 | T | Same sale; clock 8 Dec | Effective 19.99, `onSale` false, no manual action |
| TC-CAT-032-03 | FR-032 | T | Clock exactly at sale start; then exactly at sale end | Sale active at start; not active at end (see D18) |
| TC-CAT-032-04 | FR-032 | I | Sale price equal to base; sale in EUR only; end before start | All `422` |
| TC-CAT-033-01 | FR-033 | A | Request F-TEE with no currency, with `USD`, and with `GBP` | EUR prices; USD prices only; `400` |
| TC-CAT-034-01 | FR-034 | I | Variants at 19.99, 24.99, one on sale at 14.99, one **inactive** at 9.99 | `fromPrice` 14.99 (inactive ignored) |

### 3.5 Stock display

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-040-01 | FR-040 | A | Editor sends a `quantity` field when updating a variant | `400`; stock snapshot unchanged |
| TC-CAT-040-02 | FR-040 | I | Receive an event setting quantity 5 (changed at 10:00), then a late event setting 8 (changed at 09:59), then the first event again | Quantity stays 5; the repeated event changes nothing |
| TC-CAT-040-03 | FR-040 | T | Snapshot says 20 but inventory actually holds 12 (an event was lost); run reconciliation | Snapshot corrected to 12; `last_reconcile_corrections` = 1 |
| TC-CAT-041-01 | FR-041 AC1 | A | Stock 3 | `LOW_STOCK`, quantity 3 |
| TC-CAT-041-02 | FR-041 AC2 | A | Stock 250 | `IN_STOCK`; no `quantity` field in the response |
| TC-CAT-041-03 | FR-041 | A | Stock 11, 10, 1 and 0 (boundary values) | `IN_STOCK`; `LOW_STOCK` 10; `LOW_STOCK` 1; `OUT_OF_STOCK` |
| TC-CAT-042-01 | FR-042 | A | Stock 250, read by editor through admin detail | Exact 250 shown |
| TC-CAT-043-01 | FR-043 | A | Stock 0 | `purchasable` false; excluded when `inStock=true` |
| TC-CAT-044-01 | FR-044 | T | Last heartbeat 2 minutes ago | Last known values shown; page `200` |
| TC-CAT-044-02 | FR-044 | T | Last heartbeat 6 minutes ago | `UNKNOWN`, `purchasable` false everywhere; page still `200` |

### 3.6 Lifecycle

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-050-01 | FR-050 | I | Walk Draft → Active → Archived → Draft | Every step succeeds |
| TC-CAT-050-02 | FR-050 | I | Archive a draft; restore an Active product | Both `422` (see D20) |
| TC-CAT-050-03 | FR-050 | A | `DELETE /v1/admin/products/{id}` | `405 Method Not Allowed`; product unchanged |
| TC-CAT-051-01 | FR-051 | I | Parameterised: a valid draft missing exactly **one** of description, required attribute, a variant with both prices, an image; publish each | Each `422` naming the one missing item |
| TC-CAT-051-02 | FR-051 | I | Draft missing all of them; publish | One `422` listing every problem |
| TC-CAT-052-01 | FR-052 | A | Archive F-TEE | Gone from P1 and P2; P3 returns `410` with its name |
| TC-CAT-052-02 | FR-052 | A | Archived F-TEE; `svc-orders` reads `TEE-S-BLU` via I1 | `200` with name and both prices |
| TC-CAT-053-01 | FR-053 | A | Deactivate `TEE-S-RED` | Not in P3 variants; not counted in `fromPrice` |
| TC-CAT-053-02 | FR-053 | I | F-LAPTOP (one variant); deactivate `LAP-14` | `422`, an Active product needs an active variant |

### 3.7 Media

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-060-01 | FR-060 | I | Mark a second image as primary | New image primary, old one not; never two at once |
| TC-CAT-060-02 | FR-060 | A | Link an image to `TEE-S-BLU` and `TEE-M-BLU` | P3 returns the image with both variant IDs |
| TC-CAT-060-03 | FR-060 | I | Reorder images | Positions saved; P3 returns the new order |
| TC-CAT-061-01 | FR-061 | A | Request upload URLs for a GIF, and for an 11 MB JPEG | Both rejected |
| TC-CAT-061-02 | FR-061 | I | Attach a 21st image | `422` |

### 3.8 URLs and slugs

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-070-01 | FR-070 | U | Generate slugs for "Classic Tee" and "Café & Co. T-Shirt!" | `classic-tee`; `cafe-co-t-shirt` (see D19) |
| TC-CAT-070-02 | FR-070 | I | Create a second "Classic Tee" | Slug `classic-tee-2` |
| TC-CAT-070-03 | FR-070 | I | Rename F-TEE (so `classic-tee` becomes a redirect), then create a new "Classic Tee" | New product gets `classic-tee-2`; `classic-tee` still redirects to F-TEE |
| TC-CAT-071-01 | FR-071 AC1 | A | Rename `blue-shirt` to `navy-shirt`; request `blue-shirt` | `301` to `navy-shirt` |
| TC-CAT-071-02 | FR-071 | A | Rename A → B → C; request A | `301` straight to C, no chain through B (see D23) |

### 3.9 Browse, filter and sort

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-080-01 | FR-080 | A | Browse Laptops; include one Draft and one Archived laptop in the data | F-LAPTOP (from subcategory) included; Draft and Archived excluded |
| TC-CAT-081-01 | FR-081 | A | `brand=acme,globex` | Products of either brand |
| TC-CAT-081-02 | FR-081 | T | F-TEE on sale at 14.99; filter `priceMin=10&priceMax=15` | F-TEE included (effective price used) |
| TC-CAT-081-03 | FR-081 | A | `attr.ram=16,32&brand=globex` | OR within one filter, AND between filters |
| TC-CAT-081-04 | FR-081 | A | Filter on `attr.warranty` (not filterable) | `400` |
| TC-CAT-081-05 | FR-081 | A | `inStock=true` with one product fully out of stock | That product excluded |
| TC-CAT-082-01 | FR-082 | A | Laptops with `brand=globex`; read facets | `ram` and `material` counts include only Globex products; `brand` counts ignore the brand filter itself (see D21) |
| TC-CAT-083-01 | FR-083 | A | Each sort: default, `newest`, `price_asc`, `price_desc`, `name_asc`; include two products with the same price | Correct order; equal prices always in the same order |
| TC-CAT-084-01 | FR-084 | A | 60 matching products, `limit=24`; follow cursors to the end | Pages of 24, 24, 12; no duplicates; empty `nextCursor` on the last page |
| TC-CAT-084-02 | FR-084 | A | Fetch page 1; publish a new product; fetch page 2 | No existing product duplicated or skipped |
| TC-CAT-084-03 | FR-084 | A | `limit=101`; a tampered cursor | Both `400` |
| TC-CAT-084-04 | FR-084 | A | Admin list with `page=2&size=10` | Correct slice; correct `totalCount` |

### 3.10 Bulk import

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-090-01 | FR-090 | I | CSV with one new SKU and one existing SKU | New one created; existing one updated |
| TC-CAT-090-02 | FR-090 | A | CSV with 10,001 rows | Rejected at upload; no job created |
| TC-CAT-090-03 | FR-090 | A | Editor uploads a CSV | `403` |
| TC-CAT-091-01 | FR-091 | T | Upload a valid CSV; poll the job | `202` with `jobId` immediately; status moves `QUEUED` → `RUNNING` → `COMPLETED` with counts |
| TC-CAT-092-01 | FR-092 AC1 | I | 100 rows; row 37 has no EUR price | 99 saved; report: row 37, `PRICE_EUR_MISSING` |
| TC-CAT-092-02 | FR-092 | I | File that isn't valid CSV | Job `FAILED` with a reason; nothing saved |
| TC-CAT-093-01 | FR-093 | I | Import creates a new product | Status `DRAFT` |
| TC-CAT-093-02 | FR-093, NFR-008 | I | Import row removes the EUR price from an Active product's variant | Row fails with a reason; product unchanged (see D22) |

### 3.11 Audit

| Test ID | Covers | Level | Scenario | Expected |
|---|---|---|---|---|
| TC-CAT-100-01 | FR-100 | I | Editor changes F-TEE's name and a price in one save | Rows for each field with user, time, old and new values, all sharing one `change_id` |
| TC-CAT-100-02 | FR-100 | I | Run an import that updates a product | Audit rows with source `IMPORT` and the job ID |
| TC-CAT-100-03 | FR-100 | I | Change a category, a brand and a template | Each audited |
| TC-CAT-101-01 | FR-101 | A | Editor reads F-TEE history; shopper tries the same | Newest first for editor; `401` for shopper |
| TC-CAT-101-02 | FR-101 | I | Using the application's database account, try to update and delete an audit row | Both refused by the database (permission denied) |

## 4. Non-functional test cases

| Test ID | Covers | Level | Scenario | Pass condition |
|---|---|---|---|---|
| TC-CAT-NFR-001-01 | NFR-001, 005 | P | F-BULK loaded; 50 requests/second for 10 minutes: 70% listings with mixed filters and sorts, 30% product pages | p95 under 200 ms; no errors |
| TC-CAT-NFR-002-01 | NFR-002 | P | F-BULK; 5 editors saving products concurrently for 5 minutes | p95 under 500 ms |
| TC-CAT-NFR-003-01 | NFR-003 | T | Change a price; change stock through the fake inventory; let a sale start | Each visible on P1 and P3 within 10 seconds |
| TC-CAT-NFR-004-01 | NFR-004 | — | Not testable in a build; measured by production uptime monitoring over each month | 99.9% of public read checks succeed |
| TC-CAT-NFR-006-01 | NFR-006 | A | Call every admin endpoint as `anon` | All `401` |
| TC-CAT-NFR-006-02 | NFR-006 | A | Call every Admin-only endpoint (categories, brands, templates, imports) as `editor` | All `403` |
| TC-CAT-NFR-006-03 | NFR-006 | A | Call internal endpoints without service credentials | All `401` |
| TC-CAT-NFR-006-04 | NFR-006 | A | Send fields the client shouldn't control (`status` on update, `created_by`, `published_at`) on create or update | Rejected or ignored; server values kept |
| TC-CAT-NFR-007-01 | NFR-007 | A | Trigger one error of each status code | Every body has `code`, `message`, `traceId`; validation errors also have `details` |
| TC-CAT-NFR-008-01 | NFR-008 | I | Parameterised on an Active product: remove the only image, remove a EUR price, deactivate the last variant, clear a required attribute | Each `422`; product unchanged |

For the performance tests, a load-testing tool such as Gatling or k6 replays the traffic mix against a staging environment sized like production. Numbers from a developer laptop don't count.

## 5. Coverage summary

| Area | Requirements | Tests |
|---|---|---|
| Products and variants | 001–004 | 13 |
| Attributes | 010–012 | 8 |
| Categories and brands | 020–023 | 9 |
| Pricing | 030–034 | 10 |
| Stock display | 040–044 | 10 |
| Lifecycle | 050–053 | 9 |
| Media | 060–061 | 5 |
| Slugs | 070–071 | 5 |
| Browse, filter, sort | 080–084 | 12 |
| Bulk import | 090–093 | 8 |
| Audit | 100–101 | 5 |
| Non-functional | NFR-001–008 | 10 |
| **Total** | | **104** |

Every functional requirement has at least one test. NFR-004 is verified by production monitoring rather than a build test.

---

# Part 5 — Implementation Plan

## 1. How to work through this plan

The build is split into 29 small tasks in 6 milestones. Each task lists the requirements it delivers and the tests from Part 4 that prove it's done.

**Work test-first, one task at a time:**

1. Write the task's listed tests. They fail, because the feature doesn't exist yet.
2. Build until those tests pass.
3. Run the whole suite, so nothing built earlier has broken.

**Spec first, always.** If building a task reveals that the spec is wrong or incomplete, stop. Update the requirement (Part 1), the contract or model (Parts 2–3) and the tests (Part 4) first, then write the code. That keeps this document true, which is the whole point of spec-driven development.

### Definition of Done (every task)

- All tests listed for the task pass, and all earlier tests still pass.
- Any schema change is a new migration file; existing migrations are never edited.
- Every write produces audit rows in the same transaction (from T-06 onwards).
- Endpoints match Part 2 exactly: paths, status codes, field names, error format.
- Test names include their `TC-CAT-...` IDs.

### Sizes

| Size | Meaning |
|---|---|
| **S** | About 1 day |
| **M** | 2–3 days |
| **L** | 4–5 days |

These add up to roughly **14 working weeks for one developer**. That's a planning figure to refine as tasks are finished, not a promise.

## 2. Milestone overview

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ M0           │   │ M1           │   │ M2           │   │ M3           │   │ M4           │   │ M5           │
│ Foundation   │──▶│ Reference    │──▶│ Product      │──▶│ Stock and    │──▶│ Bulk import  │──▶│ Hardening    │
│ T-01..T-06   │   │ data         │   │ management   │   │ storefront   │   │ and history  │   │ and release  │
│              │   │ T-07..T-08   │   │ T-09..T-15   │   │ T-16..T-22   │   │ T-23..T-25   │   │ T-26..T-29   │
└──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘
```

By the end of M2 staff can manage the whole catalog. M3 opens it to shoppers. M4 and M5 add bulk tools and make it production-ready. M4 depends only on M2, so it can run in parallel with M3 if two people are working.

## 3. Tasks

### M0 — Foundation

These tasks build the base everything else stands on. T-01 to T-03 don't implement a requirement themselves, so they have their own done-when checks instead of test IDs.

**T-01 — Project skeleton** · S · Depends on: nothing
Spring Boot application with packages split by feature rather than by layer: `brand`, `category`, `attribute`, `product`, `pricing`, `media`, `stock`, `listing`, `importer`, `audit`, `common`. Splitting by feature keeps everything about one concept together, so a change to pricing touches one folder instead of controller, service and repository folders across the codebase. Configuration profiles for local, test and production; a health-check endpoint.
*Done when:* the app builds, starts, and the health check returns OK.

**T-02 — Database migrations** · S · Depends on: T-01
Set up Flyway to apply versioned schema files in order, and seed the `currency` table with EUR and USD. *Trade-off:* Liquibase is the main alternative. It can target several database types and generate rollbacks, but it describes changes in XML or YAML. This project only uses MySQL, and Flyway's plain SQL files are easier to read and review.
*Done when:* migrations apply cleanly to an empty MySQL started by Testcontainers.

**T-03 — Test harness** · M · Depends on: T-02
Shared base for integration and API tests with a real MySQL container; injectable clock; fake inventory client; builders for fixtures F-TREE to F-CLOCK; the coverage gate script that compares requirement IDs in this document with test IDs in the code. At first the gate only *reports* uncovered requirements; T-29 makes it fail the build.
*Done when:* an empty sample test runs on the container; the coverage report lists every requirement as uncovered.

**T-04 — API conventions** · M · Depends on: T-01
`/v1` prefix, the shared error format with `traceId`, mapping of errors to `400` / `409` / `422` (including D24), money written as decimal strings, currency parameter handling, rejection of unknown fields.
*Requirements:* NFR-007 · *Tests:* TC-CAT-NFR-007-01

**T-05 — Security** · M · Depends on: T-04
Login through the identity provider using signed tokens (JWT, JSON Web Tokens: a tamper-proof token stating who the user is and their roles). Roles Editor and Admin, service credentials for `/internal`, and path rules for the three audiences. The permission test is written to run against *every* endpoint automatically, so each new endpoint is covered as it's added.
*Requirements:* NFR-006 · *Tests:* TC-CAT-NFR-006-01

**T-06 — Audit infrastructure** · M · Depends on: T-02
`audit_log` table; the application's database account gets insert and read permission only on it; a shared mechanism that records changed fields with one `change_id` per save, inside the same transaction as the change. Built early because every later write depends on it.
*Requirements:* FR-100 (mechanism), FR-101 · *Tests:* TC-CAT-101-02

### M1 — Reference data

**T-07 — Brands and categories** · M · Depends on: T-06
Brand management (A21, P6). Category tree with parent, path and depth, including move and reorder, and the delete rules (A16–A18, P4).
*Requirements:* FR-020, 022, 023 · *Tests:* TC-CAT-020-01, 020-02, 022-01, 022-02, 022-03

**T-08 — Attribute templates** · M · Depends on: T-07
Templates with all 5 types, required and filterable flags, select options, inheritance down the tree, and the no-redefinition rule (A19, A20). Public category detail with filterable attributes (P5).
*Requirements:* FR-010, 011 · *Tests:* TC-CAT-010-01, 010-02, 010-03, 011-01, 011-02, 100-03

### M2 — Product management

**T-09 — Slug generator** · S · Depends on: T-01
Pure logic for turning names into slugs, following D19.
*Requirements:* FR-070 · *Tests:* TC-CAT-070-01

**T-10 — Product drafts** · L · Depends on: T-08, T-09
Create, read, update and delete-protection for products as drafts (A1, A3, A4): primary and extra categories, brand, attribute values validated against the template, the `product_slug` table with `-2` suffixes, and the version check.
*Requirements:* FR-001, 004, 012, 021, 023, 070 · *Tests:* TC-CAT-001-01, 001-02, 004-01, 012-02, 012-03, 023-01, 070-02

**T-11 — Options, variants and SKUs** · L · Depends on: T-10
Up to 3 options, variants with `option_signature`, the SKU uniqueness rule, add/edit/deactivate/activate (A5–A8). Rejects any attempt to send stock quantities.
*Requirements:* FR-002, 003, 040, 053 · *Tests:* TC-CAT-002-01 to 002-05, 003-03, 040-01

**T-12 — Prices and sales** · M · Depends on: T-11
Base and sale prices per currency, the shared sale window, validation rules, and the effective-price calculation (sale active from start inclusive to end exclusive, D18).
*Requirements:* FR-030, 031, 032 · *Tests:* TC-CAT-030-02, 031-01, 031-02, 032-04

**T-13 — Product version across children** · S · Depends on: T-12
Any change to variants, prices, options, attributes or images increases the product's version, so conflict protection works at product level.
*Requirements:* FR-004 · *Tests:* TC-CAT-004-02

**T-14 — Images** · M · Depends on: T-10
Pre-signed upload URLs with type and size checks (A12), attach (A13), reorder and primary (A14), remove (A15), variant links, 20-image limit. Tests use MinIO, an S3-compatible storage server that runs in Docker, so no real cloud account is needed.
*Requirements:* FR-060, 061 · *Tests:* TC-CAT-060-01, 061-01, 061-02

**T-15 — Lifecycle and publish rules** · L · Depends on: T-11, T-12, T-14
Publish, archive and restore actions (A9–A11) with the allowed transitions (D20); full publish validation returning every problem at once; SKU freeze after first publish; "an Active product stays valid" on every edit, including the last-variant rule.
*Requirements:* FR-003, 012, 030, 050, 051, 053, NFR-008 · *Tests:* TC-CAT-003-01, 003-02, 012-01, 030-01, 050-01, 050-02, 050-03, 051-01, 051-02, 053-02, NFR-008-01

**M2 checkpoint:** staff can build and publish a complete catalog through the admin API.

### M3 — Stock and storefront

**T-16 — Stock snapshot** · M · Depends on: T-11
`variant_stock_snapshot` and `inventory_sync_status` tables; consumer for "stock changed" events that ignores older or repeated ones; heartbeat tracking; 15-minute and startup reconciliation (Part 3, section 10). Admin detail shows exact quantities.
*Requirements:* FR-040, 042, 044 · *Tests:* TC-CAT-040-02, 040-03, 042-01

**T-17 — Product detail page** · L · Depends on: T-15, T-16
P3: price object in the requested currency, availability object with the 10-unit threshold and the 5-minute fallback, breadcrumb, images, `301` for old slugs (straight to current, D23), `410` for archived, `404` for drafts.
*Requirements:* FR-001, 021, 032, 033, 041, 043, 044, 060, 070, 071 · *Tests:* TC-CAT-001-03, 021-02, 032-01, 032-02, 032-03, 033-01, 041-01, 041-02, 041-03, 044-01, 044-02, 060-02, 060-03, 070-03, 071-01, 071-02

**T-18 — Internal SKU lookups** · S · Depends on: T-15
I1 and I2 for other modules, including archived products, behind service credentials.
*Requirements:* FR-052, NFR-006 · *Tests:* TC-CAT-052-02, NFR-006-03

**T-19 — Listing read model** · L · Depends on: T-16, T-17
`product_listing` table, refreshed in the same transaction as any relevant change and by a 5-second scheduler for sales starting or ending and for stock updates.
*Requirements:* FR-034, 053, NFR-003 · *Tests:* TC-CAT-034-01, 053-01, NFR-003-01

**T-20 — Browse** · L · Depends on: T-19
P1: category subtree, brand, price, stock and attribute filters, the four sorts with a stable tie-breaker, cursor pagination.
*Requirements:* FR-020, 021, 043, 080, 081, 083, 084 · *Tests:* TC-CAT-020-03, 021-01, 043-01, 080-01, 081-01 to 081-05, 083-01, 084-01, 084-02, 084-03

**T-21 — Facets** · M · Depends on: T-20
P2 counts, each filter's counts ignoring its own selection (D21).
*Requirements:* FR-052, 082 · *Tests:* TC-CAT-052-01, 082-01

**T-22 — Admin product list** · S · Depends on: T-15
A2 with numbered pages, status/category/brand filters, and search by name or SKU.
*Requirements:* FR-084 · *Tests:* TC-CAT-084-04

**M3 checkpoint:** the storefront can browse, filter and view products with live prices and stock.

### M4 — Bulk import and history

**T-23 — Import upload and jobs** · M · Depends on: T-15
A22 and A23: file checks, the 10,000-row limit, `import_job` table, a background worker that picks the oldest queued job, whole-file failure handling.
*Requirements:* FR-090, 091, 092 · *Tests:* TC-CAT-090-02, 090-03, 092-02

**T-24 — Import row processing** · L · Depends on: T-23
Match rows by SKU, create new products as drafts, update existing ones through the same validation as the admin API (D22), per-row error report (A24), audit rows marked as coming from the import.
*Requirements:* FR-090–093, 100 · *Tests:* TC-CAT-090-01, 091-01, 092-01, 093-01, 093-02, 100-02

**T-25 — Product history** · S · Depends on: T-06, T-15
A25: a product's changes, newest first, including its variants, prices and images.
*Requirements:* FR-100, 101 · *Tests:* TC-CAT-100-01, 101-01

### M5 — Hardening and release

**T-26 — Security sweep** · S · Depends on: all endpoints built
Role checks on every Admin-only endpoint, and protection of server-controlled fields.
*Requirements:* NFR-006 · *Tests:* TC-CAT-NFR-006-02, NFR-006-04

**T-27 — Performance** · M · Depends on: T-20, T-21
Build the F-BULK data generator and load scripts, run them against a production-sized staging environment, and tune indexes or add short-lived caching until targets are met.
*Requirements:* NFR-001, 002, 005 · *Tests:* TC-CAT-NFR-001-01, NFR-002-01

**T-28 — Production monitoring** · S · Depends on: deployment environment
Uptime checks on public reads, p95 response-time dashboards, and alerts.
*Requirements:* NFR-004 · *Tests:* TC-CAT-NFR-004-01

**T-29 — Release gate** · S · Depends on: all tasks
The coverage gate now fails the build if any requirement lacks a test. All 104 tests pass. Spec, tests and code agree.
*Done when:* all of the above hold.

## 4. Traceability: tests per task

| Task | Tests | Task | Tests | Task | Tests |
|---|---|---|---|---|---|
| T-04 | 1 | T-12 | 4 | T-20 | 13 |
| T-05 | 1 | T-13 | 1 | T-21 | 2 |
| T-06 | 1 | T-14 | 3 | T-22 | 1 |
| T-07 | 5 | T-15 | 11 | T-23 | 3 |
| T-08 | 6 | T-16 | 3 | T-24 | 6 |
| T-09 | 1 | T-17 | 16 | T-25 | 2 |
| T-10 | 7 | T-18 | 2 | T-26 | 2 |
| T-11 | 7 | T-19 | 3 | T-27 | 2 |
| | | | | T-28 | 1 |

**Total: 104**. Every test in Part 4 is assigned to exactly one task.
