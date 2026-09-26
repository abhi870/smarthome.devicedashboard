# CLAUDE.md — Smart Home Appliance Backend (`devicedashboard`)

Guidance for Claude (and humans) working in this repository. Read this before writing code.

## 1. What we are building

A backend that gives clients **one consistent API** over connected home appliances (TV, fridge, AC, oven,
washer, dryer, …) from **different vendors** whose APIs differ in style, auth, capabilities, metric names,
rate limits and reliability. External vendors (Samsung, Amazon, Cisco) are **mocked** as HTTP APIs served
by the same application; our backend calls them over HTTP exactly as it would call real vendor clouds.

Core capabilities (all must work end to end and be reviewable locally):

1. Register and manage appliances (CRUD, enable/disable).
2. Collect appliance metrics on **configurable per-appliance intervals**.
3. Keep **historical** metric data.
4. Generate **daily reports** (scheduled).
5. Generate **on-demand reports** for custom date ranges.
6. Expose enough API for a reviewer to exercise the whole workflow (incl. a "collect now" trigger).

## 2. Tech stack (from `pom.xml` — keep this table in sync with it)

| Concern | Current | Notes |
|---|---|---|
| Language | Java 17 (`java.version`) | Records, sealed types, pattern-matching `instanceof`, switch expressions. No virtual threads (21+). |
| Framework | Spring Boot 4.1.1 (Spring Framework 7) | Modular starters: `spring-boot-starter-webmvc`, `-data-jpa`; Jackson 3 (`tools.jackson.*`). |
| Build | Maven wrapper `./mvnw` | |
| DB (default) | PostgreSQL via `compose.yaml` + `spring-boot-docker-compose` | Started automatically by `spring-boot:run`. |
| DB (fallback) | H2 in-memory + `spring-boot-h2console` | Profile `h2` for running without Docker; console at `/h2-console`. |
| Persistence | Spring Data JPA (Hibernate 7) | Keep SQL portable across Postgres & H2. |
| Boilerplate | Lombok (present) | Allowed only on JPA entities/services (see §6). DTOs are records. |
| Tests | `spring-boot-starter-webmvc-test`, `-data-jpa-test` (JUnit 5, AssertJ, Mockito, MockMvc) | |

**Add when first needed** (propose the pom change in the same PR, don't add speculatively):
`spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-flyway` + `flyway-database-postgresql`,
`springdoc-openapi-starter-webmvc-ui`, `shedlock-spring` + `shedlock-provider-jdbc-template`,
test: `spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql`, `awaitility`, `archunit-junit5`,
`jacoco-maven-plugin`. Verify versions against start.spring.io / Maven Central rather than guessing.

## 3. Commands

```bash
./mvnw spring-boot:run                                  # app on :8080 + Postgres via compose (Docker required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2    # no Docker: in-memory H2
./mvnw test                                             # unit + slice tests
./mvnw verify                                           # + integration tests (*IT)
```

Seed data loads under the `dev` profile. Sample requests live in `http/*.http` (IntelliJ HTTP client) and
are mirrored as curl in `README.md`.

## 4. Architecture

Package-by-feature, hexagonal-lite. Base package: **`com.abhishek.smarthome`**.

```
com.abhishek.smarthome
├── appliance/     # registration & management
├── vendor/        # vendor integration (outbound)
│   ├── config/    # per-vendor @ConfigurationProperties + VendorConfigProvider
│   ├── auth/      # pluggable outbound auth (API key header / query), one factory per AuthType
│   ├── client/    # VendorClientProvider (RestClient per vendor), VendorClientFactory, samsung/ amazon/ cisco/ clients
│   └── adapter/   # (later) per-vendor mapping of raw metrics → canonical MetricType
├── mockvendor/    # fake vendor clouds: Samsung, Amazon, Cisco mock controllers + telemetry generator
├── collection/    # scheduling + executing metric collection, CollectionRun audit
├── metrics/       # historical metric storage & queries
├── report/        # daily + on-demand report generation & retrieval (JSON, CSV)
├── common/        # error handling (ProblemDetail), paging DTO, Clock bean, config properties
└── SmartHomeApplication.java
```

Inside each feature: `api/` (controllers + request/response records), `domain/` (entities, services, ports),
`infra/` (repositories, adapters). Rules (enforce with an ArchUnit test once added):

- Controllers never touch repositories or JPA entities — go through a service; return DTO records.
- `domain` must not depend on `api` or Spring Web.
- Only `vendor` knows vendor-specific payloads; everything downstream sees **normalized** metrics.
- `mockvendor` is a stand-in for external systems: nothing outside it may depend on it, and it must not
  depend on `vendor` (it has its own view of expected credentials and devices).
- Features talk via services/ports, never via another feature's repository.

### Key domain concepts

- **Appliance**: `UUID id`, name, `ApplianceType` enum, `Vendor` enum, `vendorDeviceId`, room,
  `collectionIntervalSeconds` (min 10, default 300), `enabled`, `nextCollectionAt`, `lastCollectedAt`,
  `createdAt/updatedAt`, `@Version`. Unique `(vendor, vendorDeviceId)`.
- **Vendor configuration**: `smarthome.vendors.<vendor>` → `base-url`, timeouts, `auth { type, name, prefix, api-key }`.
  `AuthType`: `API_KEY_HEADER` (header `name` = `prefix + api-key`), `API_KEY_QUERY` (query param `name`).
  New auth schemes = new `VendorAuthInterceptorFactory` + config; no client changes. Secrets from env vars, never logged.
- **VendorClient** (per vendor, built by `VendorClientFactory` from `VendorClientProvider`): `listDevices()` and
  `fetchMetrics(deviceIds, from, to)` returning **raw per-minute samples** — `RawMetricSample(vendor, externalId,
  timestamp, Map<String,Object> metrics, JsonNode raw)`. Clients parse only the envelope (device id, timestamp);
  metric names/units/structure stay vendor-specific. Vendor failures surface as typed exceptions
  (`VendorAuthException`, `VendorNotFoundException`, `VendorUnavailableException`, `VendorRateLimitedException`).
- **VendorAdapter** (later step): maps raw vendor metrics → canonical `MetricType`
  (`POWER_W`, `ENERGY_KWH`, `TEMPERATURE_C`, `HUMIDITY_PCT`, `RUNTIME_MIN`, `DOOR_OPEN_COUNT`, `STATUS`);
  unknown metrics are logged and dropped.
- **Mock vendor APIs** (`/api/v1/{samsung|amazon|cisco}/devices...`): deliberately different auth (header,
  bearer, query param), range params (ISO / epoch-ms / since+limit), time formats and payload shapes; one sample
  per minute, deterministic from `Clock` + device type.
- **MetricReading**: applianceId, metricType, value (`double`), unit, `recordedAt` (vendor time), `collectedAt`.
  Append-only; index `(appliance_id, metric_type, recorded_at)`; unique on the same triple for idempotency.
- **CollectionRun**: one row per attempt — status `SUCCESS | PARTIAL | FAILED | RATE_LIMITED | SKIPPED`,
  readings count, error message, duration, startedAt.
- **Report**: `UUID id`, type `DAILY | ON_DEMAND`, range `[from, to)`, scope (all or applianceIds),
  status `PENDING | RUNNING | COMPLETED | FAILED`, payload (JSON text column), generatedAt.
  Content per appliance per metric: min / max / avg / count, total energy kWh, collection success rate,
  first/last reading. Served as JSON (default) or CSV (`Accept: text/csv`).

### Scheduling model

- `CollectionDispatcher` ticks every `smarthome.collection.tick` (default 5s), loads due appliances
  (`enabled AND nextCollectionAt <= now`), and submits each to a bounded `ThreadPoolTaskExecutor`
  (`smarthome.collection.max-concurrency`). After each attempt set `nextCollectionAt = now + interval`
  (+ backoff on failure). Changing an interval via API takes effect on the next tick.
- `DailyReportJob` runs by cron (`smarthome.report.daily-cron`, default `0 5 0 * * *`, zone
  `smarthome.report.zone`) for the previous day; idempotent (unique on type + range + scope).
- On-demand reports: `POST /api/v1/smarthome/reports` → 202, generated async, poll `GET /api/v1/smarthome/reports/{id}`.
- Guard jobs with ShedLock when multi-instance matters; single instance is fine for local review.
- Always inject `java.time.Clock`; never call `Instant.now()` / `LocalDate.now()` directly.
- Vendor HTTP/mock calls happen **outside** DB transactions; persist results in a short transaction after.

## 5. API conventions (full details in the `/api` skill)

- Product API base path **`/api/v1/smarthome`**. JSON, camelCase. Plural nouns: `/appliances`, `/appliances/{id}/metrics`,
  `/appliances/{id}/collections`, `/reports`, `/vendors` (e.g. `/api/v1/smarthome/appliances`).
- Mock vendor APIs live under **`/api/v1/{vendor}`** (`samsung`, `amazon`, `cisco`) in the `mockvendor` package and a
  separate OpenAPI group. `smarthome` is reserved and can never be a vendor code.
- Errors: RFC 9457 `ProblemDetail` (`application/problem+json`) from one `@RestControllerAdvice`.
- Validation: Jakarta Bean Validation on request records → 400 with `errors[]` field list.
- Pagination: `page`, `size` (max 100), `sort` → `{ content, page: { number, size, totalElements, totalPages } }`.
- Time: ISO-8601 UTC `Instant`; ranges are half-open `[from, to)`; max on-demand range configurable (default 31 days).
- `POST` create → 201 + `Location`. Async → 202 + `Location`. `PATCH` partial update. `DELETE` → 204.
- Optimistic locking via `@Version` → 409 on conflict.

## 6. Coding standards

- DTOs, commands, value objects → Java **records**. Entities → classes with protected no-arg ctor.
- Lombok: allowed `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder` on entities/services.
  Forbidden: `@Data` / `@EqualsAndHashCode` on entities (breaks JPA identity), `@Setter` on entities
  (use intention-revealing methods like `appliance.changeInterval(..)`), `@SneakyThrows`.
- Constructor injection only; `final` fields; no field `@Autowired`.
- Config via `@ConfigurationProperties` records under `smarthome.*` with `@Validated`.
- No `Optional` fields/params — only return types. Explicit null handling (JSpecify `@Nullable` where useful).
- Logging: SLF4J parameterized; put `applianceId` / `vendor` in MDC during collection; never log secrets.
- Transactions at service layer (`@Transactional(readOnly = true)` for queries).
- Keep SQL/JPQL portable (Postgres + H2). If Postgres-only SQL is unavoidable, isolate it in `infra`.
- No dead or commented-out code. Javadoc on ports and non-obvious logic only.

## 7. Testing standards (full details in the `/test` skill)

- Every change ships with tests. Unit (no Spring) → slice (`@WebMvcTest`, `@DataJpaTest`) → integration
  `*IT` (`@SpringBootTest`; Postgres via Testcontainers `@ServiceConnection` once added).
- Name: `should<Outcome>_when<Condition>` + `@DisplayName` optional. Given / When / Then.
- Deterministic: fixed/mutable test `Clock`, seeded mock vendors, no `Thread.sleep` (Awaitility), no order dependence.
- Use `@MockitoBean` (not the removed `@MockBean`).

## 8. Skills and agents

| Skill | Use it to |
|---|---|
| `/api <name>` | Build a production-grade REST endpoint/resource end to end (DTOs, service, controller, errors, docs, tests). |
| `/test <target>` | Write or improve tests for a class/feature using current Spring Boot 4 practices. |
| `/db-change <change>` | Add entities/repositories/schema changes safely (and Flyway migrations once enabled). |

### Agents in `.claude/agents/`

| Agent | Use it to |
|---|---|
| `code-reviewer` | Review changes (uncommitted diff, branch or files) against this file's conventions; reports prioritized findings, never edits code. Run it before committing non-trivial work. |

## 9. Workflow for Claude

1. Read this file and the relevant skill before coding. Check existing code for patterns first.
2. Make the smallest coherent change; keep packages/feature boundaries.
3. Write/update tests with the code. Run `./mvnw verify` (or `./mvnw test` if no ITs yet) before saying done.
   For non-trivial changes, run the `code-reviewer` agent and address its blocking findings.
4. When endpoints change, update `README.md` walkthrough and `http/*.http`.
5. Finish with a summary: files changed, endpoints added, how to verify.

## 10. Do / Don't

- DO keep vendor quirks inside `vendor` clients/adapters. Build the **happy flow first**; mock imperfections
  (latency, 429, 5xx, token expiry) are added in a later step behind config that defaults to off.
- DO make jobs idempotent and restart-safe.
- DON'T add Kafka / Redis / Quartz unless asked — runnable with Maven (+ optional Docker) only.
- DON'T commit secrets; mock vendor credentials are obvious demo placeholders used as defaults in
  `application.yaml` (`${SAMSUNG_API_KEY:samsung-demo-key}`), overridable by env vars.
- DON'T change public API contracts without updating tests, OpenAPI annotations and README.
