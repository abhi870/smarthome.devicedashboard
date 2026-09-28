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
| Boilerplate | Lombok | Use it wherever it removes boilerplate (see §6). |
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

Package-by-layer. Base package: **`com.abhishek.smarthome`**.

```
com.abhishek.smarthome
├── controller/    # @RestController classes (Vendor, Device, Home, HomeDevice, DeviceReading)
├── dto/           # data classes, no records
│   ├── input/     # *Request (API bodies), *Command (service inputs), CollectionTarget
│   │   └── vendor/ device/ home/ homedevice/ reading/   # grouped by resource
│   └── output/    # *Response (API responses)
│       └── vendor/ device/ home/ homedevice/ reading/
├── entity/        # JPA entities and embeddables (Vendor, Device, MetricMapping, Home, HomeDevice, DeviceReading)
├── enums/         # all enums (VendorCode, AuthType, DeviceType, MetricType, Conversion); `enum` is a Java keyword
├── repository/    # Spring Data interfaces
├── service/       # @Service classes and domain helpers (SwitchState, DeviceReadingConverter)
├── exception/     # domain exceptions (extend common NotFound/BadRequest/Conflict)
├── schedulers/    # DeviceMetricFetchScheduler (@Scheduled, every 1 s), SchedulingConfig, CollectionProperties
├── vendor/        # vendor integration (outbound):
│   ├── config/    # per-vendor @ConfigurationProperties + VendorConfigProvider
│   ├── auth/      # pluggable outbound auth (API key header today), one factory per AuthType
│   ├── client/    # VendorClientConfig: one RestClient bean per vendor (samsungRestClient, amazonRestClient, ciscoRestClient)
│   └── adapter/   # VendorAdapter per vendor (Samsung/Amazon/Cisco): fetch + page + flatten → RawMetricSample
├── reports/       # reporting feature, layered inside: controller/ dto/ entity/ enums/ exception/ repository/
│                  # scheduler/ (DailyReportScheduler, ReportProperties) service/
├── common/        # error handling (ProblemDetail)
└── SmartHomeApplication.java
```

Tests mirror the same packages (e.g. `controller/DeviceControllerTest`, `repository/HomeDeviceRepositoryTest`).

Rules (enforce with an ArchUnit test once added):

- Controllers never touch repositories or JPA entities directly — go through a service. Controller-facing service
  methods (`get`, `list`, `register`, …) return response DTOs, mapped **inside** the service transaction;
  controllers only validate, delegate and set status/headers. Services that need another aggregate's entity use
  its entity method (`getVendor`, `getHome`, `getDevice`, `getHomeDevice`) or bulk lookup (`getVendorsById`,
  `getDevicesById`), never the DTO one.
- `entity`, `repository`, `service` must not depend on `controller` or Spring Web.
- Only `vendor` knows vendor-specific payloads; everything downstream sees **normalized** metrics.
- This project has no compile-time dependency on `../vendors`; the only contract is HTTP (base URLs in config).

### Key domain concepts

- **Vendor** (entity, table `vendor`): `UUID id`, `code` (`VendorCode` enum `SAMSUNG|AMAZON|CISCO`, unique; links to
  `smarthome.vendors.<code>` integration config), `name`, `createdAt`, `@Version`. One vendor → many catalogue devices.
- **Home** (entity, table `home`): `UUID id`, `name`, `timezone` (IANA id, default `UTC`, validated by `@TimeZoneId` → 400; daily report boundaries),
  `createdAt`, `@Version`. One home → many home devices.
- **Device** (entity, table `device`; **admin-managed catalogue** of supported models, shown to users before they
  register): `UUID id`, `vendor` FK (`@ManyToOne(LAZY)`, no getter) + read-only `vendorId`, `DeviceType` enum (`TV|REFRIGERATOR|AC|OVEN|WASHER|DRYER`),
  `model`, `name`, `metricMappings`, `createdAt`, `@Version`. Unique `(vendor_id, model)`. One device → many home devices.
- **MetricMapping** (`@Embeddable` value object, `@ElementCollection` of Device, table `device_metric_mapping`, PK
  `(device_id, external_metric)`): the complete recipe for reading a vendor metric — `externalMetric` (vendor name;
  dotted path for nested payloads) in `externalUnit` → `metric` (`MetricType`) in `internalUnit` via
  `internal = external × factor + offset` (`factor`/`offset` are `NUMERIC(30,15)` / `BigDecimal`, column
  `value_offset`). Admin sends either the recipe or a `conversion` preset (`Conversion` enum only fills the recipe;
  mixing both → 400); neither = identity. Rules (400 via `InvalidMetricMappingException`): `internalUnit` must equal
  `metric.getUnit()` (so readings of one metric are comparable across vendors), `factor != 0`, SWITCH is identity,
  optional `sample {external, expected}` must convert within `0.01 + 1e-4·|expected|`, an external metric is mapped
  at most once per device. Replace the full set with `PUT /devices/{id}/mappings`. Adding a vendor/unit = data only.
- **MetricType** (`metrics.domain`, defines each metric's canonical/internal unit): `TEMPERATURE` (C), `POWER` (W), `ENERGY` (kWh), `HUMIDITY` (%), `RUNTIME` (min),
  `DOOR_OPEN_COUNT`, `SWITCH` (on/off stored as 1/0; text like `on`/`OFF`/`ACTIVE`/`IDLE` parsed by `SwitchState`,
  conversion `NONE`); each has one canonical `getUnit()`. **Conversion**: `NONE|F_TO_C|K_TO_C|KW_TO_W|WH_TO_KWH|
  SECONDS_TO_MINUTES|HOURS_TO_MINUTES`, presets only (`externalUnit`, `factor`, `offset`). New unit = a mapping with its own factor/offset, no code.
- **HomeDevice** (entity, table `home_device`; the physical appliance a user registered): `UUID id`,
  `home` and `device` FKs (`@ManyToOne(LAZY)`, no getters) + read-only `homeId` / `deviceId` (vendor = the device's
  `vendorId`), `externalDeviceId`
  (id at the vendor), `name`, `pollingIntervalSeconds` (default 300, 60–86400), `enabled`, `nextRunAt` (= registration time →
  first run immediately; then `lastRunAt + interval` via `markRun(now)`; `changePollingInterval(s, now)` reschedules
  to `max(now, lastRunAt + s)`), `lastRunAt`, `createdAt/updatedAt`, `@Version`. Due query:
  `findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(now, Limit)` on index `(enabled, next_run_at)`;
  `PUT /home-devices/{id}/polling-interval` `{pollingIntervalSeconds}`. Unique `(device_id, external_device_id)`.
  This is what metrics are collected for.
- **Relationships map foreign keys only — never navigate them.** Each `@ManyToOne(LAZY)` exists so inserts write
  the FK; it has `@Getter(AccessLevel.NONE)` and a read-only twin column (`@Column(name = "x_id", insertable = false,
  updatable = false) UUID xId`) that code reads instead. No inverse `@OneToMany` lists, no `@EntityGraph`, no fetch
  joins.
- **Related data is loaded in bulk through repositories, in the service**: collect the FK ids
  (`EntityLookups.idsOf`), load them in one query (`VendorService.getVendorsById`,
  `DeviceCatalogService.getDevicesById` → `findAllById`), then pair rows in memory (`HomeDeviceService
  .withDeviceAndVendor`). Query count is fixed per use case (home-device list: 3; scheduler batch: 4), never per
  row — `HomeDeviceServiceQueryCountTest` pins it. Response DTOs / snapshots take their parts explicitly
  (`DeviceResponse.from(device, vendor)`, `HomeDeviceResponse.from(homeDevice, device, vendor)`,
  `CollectionTarget.of(homeDevice, device, vendor)`).
- Exception: `Device.metricMappings` is a value collection (part of the device, not a relationship); it is read
  from the device and loaded with one `IN (...)` query per list via `hibernate.default_batch_fetch_size: 50`.
- `spring.jpa.open-in-view: false` (no persistence context in the web layer). Reports use projection queries.
- Schema is owned by **Flyway** (`src/main/resources/db/migration`); never `ddl-auto=update`.
- **Vendor configuration**: `smarthome.vendors.<vendor>` → `base-url`, timeouts, `auth { type, name, prefix, api-key }`.
  `AuthType`: `API_KEY_HEADER` (header `name` = `prefix + api-key`; all vendors use this today).
  New auth schemes = new `VendorAuthInterceptorFactory` + config; no client changes. Secrets from env vars, never logged.
- **Vendor clients** (`vendor.client.VendorClientConfig`): one `RestClient` bean per vendor (`samsungRestClient`,
  `amazonRestClient`, `ciscoRestClient`), built at startup from `VendorConfigProvider` — base URL, JDK HttpClient with
  `connectTimeout`/`readTimeout`, auth interceptor from `VendorAuthRegistry`. Each adapter injects its client with
  `@Qualifier(VendorClientConfig.AMAZON)` (Lombok copies `@Qualifier` to the constructor via `lombok.config`). A vendor
  without config fails startup. New vendor = config + `@Bean` method + adapter.
- **VendorAdapter** (`vendor.adapter`, one `@Component` per vendor, looked up by `VendorAdapterRegistry.adapterFor(code)`):
  `fetchMetrics(externalDeviceId, from, to)` → per-minute `RawMetricSample(time, Map<String,Object> metrics)` in
  `[from, to)` in one call per device (no pagination; the collector never asks for more than 24 h). Uses its injected per-vendor `RestClient`; bodies read as
  plain maps. Flattening: Samsung `components.main.<capability>.<attribute>.value` → `<capability>.<attribute>`;
  Amazon (one call per device: `/devices/{id}/metrics`) `properties[{name,value}]` → `name`; Cisco flat keys minus `ts`. Names/units stay vendor-specific.
- **Collection** (`metrics.domain.service.MetricCollectionService`, driven by `schedulers.DeviceMetricFetchScheduler`):
  due targets (`HomeDeviceService.findDueTargets(batch)` → `CollectionTarget` snapshots incl. mappings) → range
  `[lastRunAt ?? now - interval, now)` capped at 24 h → adapter fetch (no transaction) → `DeviceReadingConverter`
  (mapping recipe; SWITCH → `ON`/`OFF`; numbers as plain text, 6 decimals; unmapped metrics ignored, unreadable values
  skipped with a warning) → `DeviceReadingService.saveCollected` (one transaction: skip existing
  (metric, time), `saveAll`, `recordRun` → `nextRunAt = now + interval`). On failure `scheduleRetry(retryDelay)`
  (capped at the interval, `lastRunAt` unchanged so the gap is re-fetched); other devices continue.
- **Mock vendor APIs** (served by `../vendors` at `http://localhost:8081/api/v1/{samsung|amazon|cisco}/devices...`):
  one call per device, deliberately different auth headers (`X-API-Key`, `Authorization: Bearer`, `X-Cisco-Api-Key`),
  range params (ISO from/to, epoch-ms startTime/endTime, epoch-s since + limit minutes), time formats and
  payload shapes. Data is a recorded day per device (1440 per-minute samples) replayed for any date, energy counters
  never decreasing.
- **DeviceReading** (entity, table `device_reading`, `metrics.domain`): `UUID id`, `homeDeviceId` (plain UUID column
  with FK — no JPA relationship, hot path), `metric` (`MetricType`), `time` (vendor time; column `reading_time`),
  `value` (string, column `reading_value VARCHAR(100)`: a number for numeric metrics — reports use
  `CAST(reading_value AS DOUBLE PRECISION)` — or a state like `ON` for SWITCH), `unit` (string, e.g. `W`, `C`,
  `on/off`; defaults to `metric.getUnit()`), `collectedAt`. Append-only, no `@Version`. Unique index `(home_device_id, metric, reading_time DESC)` —
  idempotent collection and fast per-device-per-metric report queries; index `(home_device_id, reading_time DESC)`.
  API: `POST /readings` body `{homeDeviceId, metric, time, value, unit?}` (201; duplicate → 409),
  `GET /readings?homeDeviceId=&startDate=&endDate=[&metric=]` (ISO instants, `[start, end)`, newest first).
- **Reports** (package `reports`, migration `V5__create_reports.sql`) — per home device:
  - **Report** (table `report`): `type` `DAILY` (one per device and home-local day, by the daily job) or
    `CUSTOM_RANGE` (on request); `localDate` (DAILY only), `periodStart/periodEnd` (`[start, end)`, UTC),
    `timezone` (snapshot of `Home.timezone`), `metrics` (comma-separated), `status`
    `QUEUED | RUNNING | SUCCEEDED | FAILED`, `attempts`, `nextAttemptAt`, `error`, `coveragePct`, `requestedAt`,
    `completedAt`, `@Version`. Unique `(home_device_id, local_date)` — NULL for CUSTOM_RANGE — makes the daily job
    idempotent. `summaries`: `@ElementCollection` of `MetricSummary` (`report_metric_summary`: metric, unit,
    sampleCount, min, max, time-weighted avg, total, coveragePct); `dailyValues`: `DailyValue` per metric and day
    (`report_daily_value`, CUSTOM_RANGE only; total for counters/switch, avg for gauges).
  - **DailyMetricRollup** (table `daily_metric_rollup`, read model): per device, home-local day and metric — sample
    count, min/max/avg, first/last reading, total, covered/period seconds, coverage. Written with the DAILY report
    in one transaction; custom ranges read them for full days and raw readings for the partial edge days (and days
    without rollups yet).
  - **Aggregation** (`AggregationKind`, `MetricStatisticsCalculator`): GAUGE (temperature, power, humidity) →
    min / time-weighted avg / max; COUNTER (energy, runtime, door count — cumulative meters) → total = sum of
    increases from the last reading before the period (drops = resets, ignored), so days add up exactly; SWITCH →
    total = minutes on. A reading holds until the next one, capped at `max-sample-gap` (15m); longer gaps lower
    coverage.
  - API: `POST /home-devices/{id}/reports` `{from, to, metrics?}` → 201 + SUCCEEDED report (synchronous; range
    ≤ `max-range-days`, metrics must be mapped by the device, default all); `GET /home-devices/{id}/reports
    ?type=&from=&to=` (ISO dates, home-local, inclusive; default last 31 days); `GET /reports/{id}`;
    `POST /reports/{id}/regenerate` (recompute after late data; DAILY also recomputes its rollups).

### Scheduling model

- `DeviceMetricFetchScheduler` runs `@Scheduled(fixedDelay = 1 s)` (ticks never overlap) when
  `smarthome.collection.enabled` (default true),
  collecting up to `smarthome.collection.batch-size` (50) due devices **in parallel** on the `collectionExecutor` pool
  (`parallelism`, 8 threads) with at most `max-concurrent-per-vendor` (4) calls in flight per vendor
  (`VendorCallLimiter`, one semaphore per vendor); the tick waits for all devices. `retry-delay` (60s) after failures.
  Changing an interval via API takes effect on the next tick. Single instance; add ShedLock before scaling out.
- `DailyReportScheduler` runs every `smarthome.reports.tick` (15 min) when `smarthome.reports.enabled`.
  `DailyReportPlanner` (read-only tx) finds due device-days: per enabled home device, home-local days from
  `max(registration day, last finished day − backfill-days + 1)` to the last day whose midnight is ≥ `grace-period`
  (1h) ago, without a report or with a FAILED one due for retry (at most `batch-size`). `DailyReportService`
  generates each in its own transaction (`ReportGenerator`); a failure is stored on the report (`FAILED`,
  `nextAttemptAt = now + retry-delay × attempts`, up to `max-attempts`). `SchedulingConfig` enables scheduling
  unconditionally; each job has its own `enabled` switch. Device-days run in parallel on the `reportExecutor` pool
  (`smarthome.reports.parallelism`, 4); the tick waits for all of them.
- **Threads**: `@Scheduled` ticks run on a scheduler pool of 2 (`spring.task.scheduling.pool.size`), so collection
  and the daily job never block each other; their work fans out to the two bounded pools in
  `common/config/TaskExecutorsConfig` (queue = one batch, finish running tasks on shutdown). Hikari pool 20.
  Everything a task touches is its own (snapshot, transaction, rows); shared beans are immutable after startup.
- **Batched inserts**: `hibernate.jdbc.batch_size=50` + `order_inserts`. Entities with an assigned UUID id that are
  bulk-inserted (`DeviceReading`, `DailyMetricRollup`) implement `Persistable` (`isNew` until persisted/loaded), so
  `saveAll` persists instead of merging (no SELECT per row). Do the same for any new bulk-inserted entity.
- On-demand reports are synchronous (201 with the report); full days come from rollups, so a year-long range reads
  ~365 rollup rows per metric plus two partial days of raw readings.
- Guard jobs with ShedLock when multi-instance matters; single instance is fine for local review.
- Current time: services call `Instant.now()` (always UTC) and pass it into entity methods (`register(.., now)`,
  `markRun(now)`), so entities stay pure and are tested with fixed instants. Never `LocalDateTime.now()`/`LocalDate.now()`.
- **All timestamps are UTC**: entities/DTOs use `Instant` only (no `LocalDateTime`), columns are
  `TIMESTAMP WITH TIME ZONE`, Hibernate `jdbc.time_zone=UTC` + `timezone.default_storage=NORMALIZE_UTC`, JVM default
  timezone forced to UTC in `SmartHomeApplication.main`, Postgres container runs with `timezone=UTC`, and JSON uses
  ISO-8601 with `Z`. Local time (e.g. `Home.timezone`) is applied only when computing report day boundaries.
- Vendor HTTP/mock calls happen **outside** DB transactions; persist results in a short transaction after.

## 5. API conventions (full details in the `/api` skill)

- Product API base path **`/api/v1/smart-home`**. JSON, camelCase. Plural nouns: `/vendors`, `/homes`, `/devices`, `/home-devices`,
  `/home-devices/{id}/metrics`, `/home-devices/{id}/collections`, `/reports`.
- Registration endpoints: `POST /api/v1/smart-home/{vendors|homes|devices|home-devices}/register` → 201 with the created
  resource as the body (no `Location` header — responses never carry URIs); `GET /api/v1/smart-home/{res}/{id}`. Catalogue devices reference `vendorId`;
  home devices reference `homeId` + `deviceId` (UUIDs). List endpoints: `GET /vendors`, `GET /homes`,
  `GET /devices?vendorId=&deviceType=` (supported devices), `GET /home-devices?homeId=`;
  `PUT /devices/{id}/mappings` replaces a device's metric mappings (200).
- Mock vendor APIs live in the separate `../vendors` service under **`/api/v1/{vendor}`** (`samsung`, `amazon`, `cisco`).
  `smart-home` is reserved and can never be a vendor code.
- Errors: RFC 9457 `ProblemDetail` (NotFound→404, BadRequest→400, Conflict→409) (`application/problem+json`) from one `@RestControllerAdvice`.
- Validation: Jakarta Bean Validation on request classes → 400 with `errors[]` field list.
- Pagination: `page`, `size` (max 100), `sort` → `{ content, page: { number, size, totalElements, totalPages } }`.
- Time: ISO-8601 UTC `Instant`; ranges are half-open `[from, to)`; max on-demand range configurable (default 31 days).
- `POST` create → 201 + body (`@ResponseStatus(HttpStatus.CREATED)`), never a `Location` header or any URI in
  responses; clients use the returned `id`. Async → 202 + body with the job `id`. `PATCH` partial update. `DELETE` → 204.
- Optimistic locking via `@Version` → 409 on conflict.

## 6. Coding standards

- **Never use Java `record` types** in this project (main or test code). DTOs, commands, value objects and
  configuration properties are regular classes: `private final` fields, one constructor, JavaBean getters
  (`getX()` / `isX()`), and `equals`/`hashCode`/`toString` only when needed (never print secrets).
  Entities → classes with protected no-arg ctor.
- Lombok wherever it removes boilerplate: `@Getter` instead of hand-written getters (DTOs, commands, config
  properties, entities), `@RequiredArgsConstructor` for constructor injection and plain all-final value classes,
  `@NoArgsConstructor(access = PROTECTED)` for the JPA constructor, `@Slf4j` for logging. Keep a hand-written
  constructor only when it does work (validation, defensive copies, `@JsonCreator`/`@DefaultValue` parameters).
  Never `@Data`/`@Setter`/`@EqualsAndHashCode` on entities; keep secret-masking `toString()` overrides.
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
- Deterministic: fixed instants for entity logic; service tests assert "now" with `isBetween(before, after)`; seeded mock vendors, no `Thread.sleep` (Awaitility), no order dependence.
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
