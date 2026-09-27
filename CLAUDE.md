# CLAUDE.md — Smart Home Appliance Backend (`devicedashboard`)

Guidance for Claude (and humans) working in this repository. Read this before writing code.

## 1. What we are building

A backend that gives clients **one consistent API** over connected home appliances (TV, fridge, AC, oven,
washer, dryer, …) from **different vendors** whose APIs differ in style, auth, capabilities, metric names,
rate limits and reliability. External vendors (Samsung, Amazon, Cisco) are **mocked** by a separate Spring Boot
service, **`../vendors`** (port 8081, own repo); this backend calls it over HTTP exactly as it would call real
vendor clouds. Mock vendor code never lives in this project.

Core capabilities (all must work end to end and be reviewable locally):

1. Register vendors, homes and devices (appliances); manage devices (enable/disable, interval).
2. Collect appliance metrics on **configurable per-appliance intervals**.
3. Keep **historical** metric data.
4. Generate **daily reports** (scheduled).
5. Generate **on-demand reports** for custom date ranges.
6. Expose enough API for a reviewer to exercise the whole workflow (incl. a "collect now" trigger).

## 2. Tech stack (from `pom.xml` — keep this table in sync with it)

| Concern | Current | Notes |
|---|---|---|
| Language | Java 17 (`java.version`) | Sealed types, pattern-matching `instanceof`, switch expressions. **No `record` types** (see §6). No virtual threads (21+). |
| Framework | Spring Boot 4.1.1 (Spring Framework 7) | Modular starters: `spring-boot-starter-webmvc`, `-data-jpa`; Jackson 3 (`tools.jackson.*`). |
| Build | Maven wrapper `./mvnw` | |
| DB (default) | PostgreSQL via `compose.yaml` + `spring-boot-docker-compose` | Started automatically by `spring-boot:run`. |
| DB (fallback) | H2 in-memory + `spring-boot-h2console` | Profile `h2` for running without Docker; console at `/h2-console`. |
| Persistence | Spring Data JPA (Hibernate 7) | Keep SQL portable across Postgres & H2. |
| Boilerplate | Lombok (present) | Allowed only on JPA entities/services (see §6). DTOs are plain classes. |
| Tests | `spring-boot-starter-webmvc-test`, `-data-jpa-test` (JUnit 5, AssertJ, Mockito, MockMvc) | |

**Add when first needed** (propose the pom change in the same PR, don't add speculatively):
`spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-flyway` + `flyway-database-postgresql`,
`springdoc-openapi-starter-webmvc-ui`, `shedlock-spring` + `shedlock-provider-jdbc-template`,
test: `spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql`, `awaitility`, `archunit-junit5`,
`jacoco-maven-plugin`. Verify versions against start.spring.io / Maven Central rather than guessing.

## 3. Commands

```bash
(cd ../vendors && ./mvnw spring-boot:run)              # mock vendor clouds on :8081 (start first)
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
├── device/        # Device = supported-device catalogue (admin) + DeviceCatalogService; POST /devices/register, GET /devices
├── home/          # Home entity + HomeService; POST /homes/register
├── homedevice/    # HomeDevice = appliance a user registered in a home + HomeDeviceService; POST /home-devices/register
├── vendor/        # Vendor entity + VendorService (api/ domain/) and vendor integration (outbound):
│   ├── config/    # per-vendor @ConfigurationProperties + VendorConfigProvider
│   ├── auth/      # pluggable outbound auth (API key header today), one factory per AuthType
│   ├── client/    # VendorClientProvider (RestClient per vendor), VendorClientFactory, samsung/ amazon/ cisco/ clients
│   └── (no adapters: vendor → canonical metric mapping is data, see MetricMapping on Device)
├── collection/    # scheduling + executing metric collection, CollectionRun audit
├── metrics/       # MetricType + Conversion (canonical metrics); later historical storage & queries
├── report/        # daily + on-demand report generation & retrieval (JSON, CSV)
├── common/        # error handling (ProblemDetail), paging DTO, Clock bean, config properties
└── SmartHomeApplication.java
```

Inside each feature:

```
<feature>/
├── api/                 # @RestController
│   └── dto/             # request/response classes (no records)
└── domain/
    ├── entity/          # JPA entities, embeddables, enums/value types they use
    ├── repository/      # Spring Data interfaces
    ├── service/         # @Service classes + their *Command input classes
    └── exception/       # feature exceptions (extend common NotFound/BadRequest/Conflict)
```

Rules (enforce with an ArchUnit test once added):

- Controllers never touch repositories or JPA entities — go through a service; return DTO classes.
- `domain` must not depend on `api` or Spring Web.
- Only `vendor` knows vendor-specific payloads; everything downstream sees **normalized** metrics.
- This project has no compile-time dependency on `../vendors`; the only contract is HTTP (base URLs in config).
- Features talk via services/ports, never via another feature's repository.

### Key domain concepts

- **Vendor** (entity, table `vendor`): `UUID id`, `code` (`VendorCode` enum `SAMSUNG|AMAZON|CISCO`, unique; links to
  `smarthome.vendors.<code>` integration config), `name`, `createdAt`, `@Version`. One vendor → many catalogue devices.
- **Home** (entity, table `home`): `UUID id`, `name`, `timezone` (IANA id, default `UTC`; daily report boundaries),
  `createdAt`, `@Version`. One home → many home devices.
- **Device** (entity, table `device`; **admin-managed catalogue** of supported models, shown to users before they
  register): `UUID id`, `@ManyToOne(LAZY)` `vendor`, `DeviceType` enum (`TV|REFRIGERATOR|AC|OVEN|WASHER|DRYER`),
  `model`, `name`, `metricMappings`, `createdAt`, `@Version`. Unique `(vendor_id, model)`. One device → many home devices.
- **MetricMapping** (`@Embeddable` value object, `@ElementCollection` of Device, table `device_metric_mapping`, PK
  `(device_id, external_metric)`): `externalMetric` (vendor name; dotted path for nested payloads) → `metric`
  (`MetricType`) via `conversion` (`Conversion`, default `NONE`). Admin supplies them in `mappings[]` when adding a
  device (at least one) and can replace the full set with `PUT /devices/{id}/mappings`. Rules (400 via
  `InvalidMetricMappingException`): the conversion must produce the metric's unit (`Conversion.supports(metric)`;
  `NONE` fits all) and an external metric is mapped at most once per device. This is how vendor-specific
  names/units become canonical — no per-vendor mapping code.
- **MetricType** (`metrics.domain`): `TEMPERATURE` (C), `POWER` (W), `ENERGY` (kWh), `HUMIDITY` (%), `RUNTIME` (min),
  `DOOR_OPEN_COUNT`, `SWITCH` (on/off stored as 1/0; text like `on`/`OFF`/`ACTIVE`/`IDLE` parsed by `SwitchState`,
  conversion `NONE`); each has one canonical `unit()`. **Conversion**: `NONE|F_TO_C|K_TO_C|KW_TO_W|WH_TO_KWH|
  SECONDS_TO_MINUTES|HOURS_TO_MINUTES`, `apply(double)`. New unit = new enum constant.
- **HomeDevice** (entity, table `home_device`; the physical appliance a user registered): `UUID id`,
  `@ManyToOne(LAZY)` `home` and `device` (HomeDevice owns both FKs; vendor is `device.vendor`), `externalDeviceId`
  (id at the vendor), `name`, `pollingIntervalSeconds` (default 300), `enabled`, `nextPollAt` (= registration time →
  first poll immediately), `lastPolledAt`, `createdAt/updatedAt`, `@Version`. Unique `(device_id, external_device_id)`.
  This is what metrics are collected for.
- Relationships: owning side is always the `@ManyToOne`; inverse sides (`Vendor.devices`, `Home.devices`) are
  read-only `mappedBy` lists, never cascaded. Use `@EntityGraph` finders to avoid N+1.
- Schema is owned by **Flyway** (`src/main/resources/db/migration`); never `ddl-auto=update`.
- **Vendor configuration**: `smarthome.vendors.<vendor>` → `base-url`, timeouts, `auth { type, name, prefix, api-key }`.
  `AuthType`: `API_KEY_HEADER` (header `name` = `prefix + api-key`; all vendors use this today).
  New auth schemes = new `VendorAuthInterceptorFactory` + config; no client changes. Secrets from env vars, never logged.
- **VendorClient** (per vendor, built by `VendorClientFactory` from `VendorClientProvider`): `listDevices()` and
  `fetchMetrics(deviceIds, from, to)` returning **raw per-minute samples** — `RawMetricSample(vendor, externalId,
  timestamp, Map<String,Object> metrics, JsonNode raw)`. Clients parse only the envelope (device id, timestamp);
  metric names/units/structure stay vendor-specific. Vendor failures surface as typed exceptions
  (`VendorAuthException`, `VendorNotFoundException`, `VendorUnavailableException`, `VendorRateLimitedException`).
- **Normalization** (later step): for each raw sample, look up the home device's catalogue `MetricMapping`s; mapped
  metrics are converted with `toCanonical(..)`, unmapped metrics are logged at debug and dropped.
- **Mock vendor APIs** (served by `../vendors` at `http://localhost:8081/api/v1/{samsung|amazon|cisco}/devices...`): deliberately different auth headers
  (`X-API-Key`, `Authorization: Bearer`, `X-Cisco-Api-Key`), range params (ISO / epoch-ms / since+limit), time formats and payload shapes; one sample
  per minute, deterministic from `Clock` + device type.
- **MetricReading**: deviceId, metricType, value (`double`), unit, `recordedAt` (vendor time), `collectedAt`.
  Append-only; index `(device_id, metric_type, recorded_at)`; unique on the same triple for idempotency.
- **CollectionRun**: one row per attempt — status `SUCCESS | PARTIAL | FAILED | RATE_LIMITED | SKIPPED`,
  readings count, error message, duration, startedAt.
- **Report**: `UUID id`, type `DAILY | ON_DEMAND`, range `[from, to)`, scope (home, all or deviceIds),
  status `PENDING | RUNNING | COMPLETED | FAILED`, payload (JSON text column), generatedAt.
  Content per device per metric: min / max / avg / count, total energy kWh, collection success rate,
  first/last reading. Served as JSON (default) or CSV (`Accept: text/csv`).

### Scheduling model

- `CollectionDispatcher` ticks every `smarthome.collection.tick` (default 5s), loads due devices
  (`enabled AND nextCollectionAt <= now`), and submits each to a bounded `ThreadPoolTaskExecutor`
  (`smarthome.collection.max-concurrency`). After each attempt set `nextCollectionAt = now + interval`
  (+ backoff on failure). Changing an interval via API takes effect on the next tick.
- `DailyReportJob` runs by cron (`smarthome.report.daily-cron`, default `0 5 0 * * *`, zone
  `smarthome.report.zone`) for the previous day; idempotent (unique on type + range + scope).
- On-demand reports: `POST /api/v1/smart-home/reports` → 202, generated async, poll `GET /api/v1/smart-home/reports/{id}`.
- Guard jobs with ShedLock when multi-instance matters; single instance is fine for local review.
- Always inject `java.time.Clock`; never call `Instant.now()` / `LocalDate.now()` directly.
- Vendor HTTP/mock calls happen **outside** DB transactions; persist results in a short transaction after.

## 5. API conventions (full details in the `/api` skill)

- Product API base path **`/api/v1/smart-home`**. JSON, camelCase. Plural nouns: `/vendors`, `/homes`, `/devices`, `/home-devices`,
  `/home-devices/{id}/metrics`, `/home-devices/{id}/collections`, `/reports`.
- Registration endpoints: `POST /api/v1/smart-home/{vendors|homes|devices|home-devices}/register` → 201 + `Location:
  /api/v1/smart-home/{res}/{id}`; `GET /api/v1/smart-home/{res}/{id}`. Catalogue devices reference `vendorId`;
  home devices reference `homeId` + `deviceId` (UUIDs). `GET /devices?vendorId=&deviceType=` lists supported devices;
  `PUT /devices/{id}/mappings` replaces a device's metric mappings (200).
- Mock vendor APIs live in the separate `../vendors` service under **`/api/v1/{vendor}`** (`samsung`, `amazon`, `cisco`).
  `smart-home` is reserved and can never be a vendor code.
- Errors: RFC 9457 `ProblemDetail` (NotFound→404, BadRequest→400, Conflict→409) (`application/problem+json`) from one `@RestControllerAdvice`.
- Validation: Jakarta Bean Validation on request classes → 400 with `errors[]` field list.
- Pagination: `page`, `size` (max 100), `sort` → `{ content, page: { number, size, totalElements, totalPages } }`.
- Time: ISO-8601 UTC `Instant`; ranges are half-open `[from, to)`; max on-demand range configurable (default 31 days).
- `POST` create → 201 + `Location`. Async → 202 + `Location`. `PATCH` partial update. `DELETE` → 204.
- Optimistic locking via `@Version` → 409 on conflict.

## 6. Coding standards

- **Never use Java `record` types** in this project (main or test code). DTOs, commands, value objects and
  configuration properties are regular classes: `private final` fields, one constructor, JavaBean getters
  (`getX()` / `isX()`), and `equals`/`hashCode`/`toString` only when needed (never print secrets).
  Entities → classes with protected no-arg ctor.
- Lombok: allowed `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`, `@Builder` on entities/services.
  Forbidden: `@Data` / `@EqualsAndHashCode` on entities (breaks JPA identity), `@Setter` on entities
  (use intention-revealing methods like `device.changeInterval(..)`), `@SneakyThrows`.
- Constructor injection only; `final` fields; no field `@Autowired`.
- Config via `@ConfigurationProperties` classes under `smarthome.*` with `@Validated` (constructor binding:
  single constructor, `final` fields, `@DefaultValue` on constructor params, constraints on fields).
- No `Optional` fields/params — only return types. Explicit null handling (JSpecify `@Nullable` where useful).
- Logging: SLF4J parameterized; put `deviceId` / `vendor` in MDC during collection; never log secrets.
- Transactions at service layer (`@Transactional(readOnly = true)` for queries).
- Keep SQL/JPQL portable (Postgres + H2). If Postgres-only SQL is unavoidable, isolate it in `domain/repository` behind an H2-safe alternative.
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
- DON'T commit secrets; vendor credentials are obvious demo placeholders (matching `../vendors`) used as defaults in
  `application.yaml` (`${SAMSUNG_API_KEY:samsung-demo-key}`), overridable by env vars.
- DON'T change public API contracts without updating tests, OpenAPI annotations and README.
