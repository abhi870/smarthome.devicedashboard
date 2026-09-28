# Smart Home Device Dashboard (`devicedashboard`)

One consistent REST API over connected home appliances (TV, fridge, AC, oven, washer, dryer) from different
vendors (Samsung, Amazon, Cisco) whose APIs differ in auth, request style, metric names and units. The backend
registers vendors, homes and devices, collects metrics on a per-device polling interval, keeps their history, and
produces daily and on-demand reports.

The vendor clouds are simulated by a separate service,
**[smarthome.vendors](https://github.com/abhi870/smarthome.vendors)** (port 8081), which this backend calls over
HTTP exactly as it would call the real vendor APIs. **Start that service before this one** — without it, metrics
collection fails and there is no data for readings or reports.

## How to run

**Prerequisites:** Java 17, Docker (for PostgreSQL). `jq` and `curl` for the demo scripts.

**1. Run the mock vendor service first** (required). Clone it next to this repo and start it on port 8081:

```bash
git clone https://github.com/abhi870/smarthome.vendors.git vendors
cd vendors && ./mvnw spring-boot:run
```

Check it's up: `curl -H 'X-API-Key: samsung-demo-key' http://localhost:8081/api/v1/samsung/devices` lists the Samsung
devices.

**2. Start this backend** (port 8080) in another terminal. PostgreSQL is started automatically from `compose.yaml`
(host port 5433), and Flyway creates the schema.

```bash
cd devicedashboard && ./mvnw spring-boot:run
```

If the vendor service runs elsewhere, point the backend at it with `SAMSUNG_BASE_URL`, `AMAZON_BASE_URL` and
`CISCO_BASE_URL` (see the settings table below).

Without Docker, run on in-memory H2 instead (data is lost on restart):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

### Database

There is no datasource block in `application.yaml`: when the app starts, Spring Boot's Docker Compose support runs
`compose.yaml`, waits for PostgreSQL and connects to it automatically. The container runs in UTC, and Flyway creates
the schema from `src/main/resources/db/migration` (V1–V5).

| | Value |
|---|---|
| Image | `postgres:latest` (service `postgres` in `compose.yaml`) |
| Host / port | `localhost:5433` (container port 5432) |
| Database | `mydatabase` |
| Username / password | `myuser` / `secret` (local demo values) |
| JDBC URL | `jdbc:postgresql://localhost:5433/mydatabase` |
| Connection pool | HikariCP, max 20 connections (`spring.datasource.hikari.maximum-pool-size`) |

Look at the data with any SQL client, or:

```bash
docker compose exec postgres psql -U myuser -d mydatabase
# e.g.  \dt   select count(*) from device_reading;   select type, local_date, status from report;
```

Main tables: `vendor`, `device` (+ `device_metric_mapping`), `home`, `home_device`, `device_reading`, `report`
(+ `report_metric_summary`, `report_daily_value`), `daily_metric_rollup`, and Flyway's `flyway_schema_history`.

Start from an empty database with `docker compose down -v` (deletes the volume) before the next run.

To use your own PostgreSQL instead of the compose container, turn compose support off and pass the connection:

```bash
SPRING_DOCKER_COMPOSE_ENABLED=false \
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db> \
SPRING_DATASOURCE_USERNAME=<user> SPRING_DATASOURCE_PASSWORD=<password> \
./mvnw spring-boot:run
```

Tests don't need Docker for the repository and report tests: `@DataJpaTest` runs the same Flyway migrations on
in-memory H2. `SmartHomeApplicationTests` starts the full app, so it uses the compose PostgreSQL (Docker running).

### Settings

Useful settings (`src/main/resources/application.yaml`, overridable by environment variables):

| Setting | Default | Meaning |
|---|---|---|
| `COLLECTION_ENABLED` | `true` | Metrics collection scheduler (ticks every second, collects due devices) |
| `REPORTS_ENABLED` | `true` | Daily report job |
| `smarthome.collection.parallelism` / `max-concurrent-per-vendor` | `8` / `4` | Devices collected at once / vendor calls in flight per vendor |
| `smarthome.reports.parallelism` | `4` | Device-days the daily job generates at once |
| `SAMSUNG_BASE_URL`, `AMAZON_BASE_URL`, `CISCO_BASE_URL` | `http://localhost:8081/api/v1/<vendor>` | Vendor endpoints |
| `SAMSUNG_API_KEY`, `AMAZON_API_KEY`, `CISCO_API_KEY` | demo keys | Keys the mock vendors accept |

## How to test and verify

### Automated tests

```bash
./mvnw test
```

Covers the domain logic (unit conversion, scheduling, report aggregation), the web layer (`@WebMvcTest`: status
codes, validation, error bodies), the JPA layer against the real Flyway schema (`@DataJpaTest` on H2: constraints,
query counts, report generation end to end) and each vendor adapter against mocked HTTP responses.

### Testing plan

[docs/testing-plan.md](docs/testing-plan.md) walks through registering the vendors and their device models,
the home, and the home devices with the Postman collection — expected results and negative checks for each step —
and then verifying that collection runs. The demo scripts cover the same flows from a terminal.

### Try the whole flow by hand

With both services running, on an empty database:

```bash
./scripts/demo/run-demo.sh
```

It walks through the flow step by step with the mock Amazon AC:

1. The admin registers the vendor.
2. The admin adds the device model to the catalogue with its metric mappings.
3. A user registers a home (timezone `Asia/Kolkata`).
4. The user lists the supported devices.
5. The user registers their AC with a 60 s polling interval.

Within a minute, readings appear:

```bash
curl "http://localhost:8080/api/v1/smart-home/readings?homeDeviceId=<id>&startDate=2026-09-28T00:00:00Z&endDate=2026-09-29T00:00:00Z"
```

Other ways to call the API:

- `http/devices.http` (IntelliJ HTTP client) has every endpoint, and captures ids between requests.
- `postman/smart-home.postman_collection.json` registers all six mock devices; run folders 1–4 in order, then 5–7.

### Main endpoints (base path `/api/v1/smart-home`)

| Endpoint | Purpose |
|---|---|
| `POST /vendors/register`, `GET /vendors` | Admin: vendors |
| `POST /devices/register`, `PUT /devices/{id}/mappings`, `GET /devices` | Admin: catalogue of supported device models and their metric mappings |
| `POST /homes/register`, `GET /homes` | User: homes (with an IANA timezone) |
| `POST /home-devices/register`, `PUT /home-devices/{id}/polling-interval`, `GET /home-devices` | User: appliances in a home |
| `GET /readings?homeDeviceId=&startDate=&endDate=[&metric=]`, `POST /readings` | Metric history |
| `POST /home-devices/{id}/reports` `{from, to, metrics?}` | On-demand report over a custom range (returns 201 with the report) |
| `GET /home-devices/{id}/reports?type=&from=&to=`, `GET /reports/{id}`, `POST /reports/{id}/regenerate` | Daily and on-demand reports |

Errors use RFC 9457 Problem Details (`400` validation, `404` not found, `409` duplicate).

## Assumptions, non-goals and out of scope

- **User authentication and authorization are out of scope.** The API is open; there are no users, roles or
  per-home access checks. "Admin" and "user" are roles in the workflow only.
- **Real vendor APIs are out of scope; they are mocked.** [smarthome.vendors](https://github.com/abhi870/smarthome.vendors) replays one recorded day of per-minute
  samples per device, in each vendor's own format, auth header and time-range parameters. Faults like latency, 429 and 5xx
  are not simulated yet.
- **Single instance.** The schedulers assume one running instance; running several needs a distributed lock
  (e.g. ShedLock).
- **All stored times are UTC** (`Instant`, `TIMESTAMP WITH TIME ZONE`). A home's timezone is used only to decide
  where its report days start and end.
- **Energy, runtime and door-count readings are cumulative counters.** A report's total is how much the counter
  went up; a drop is treated as a counter reset.
- **Not built:** device control (turning appliances on or off), a UI, notifications, data retention or
  downsampling of old readings, and report export formats other than JSON.

## Important design choices

- **Vendor auth is separate from the vendor client.** Each vendor gets one `RestClient`, and its
  authentication is added by a `ClientHttpRequestInterceptor`. The interceptor comes from a
  `VendorAuthInterceptorFactory` chosen by the vendor's configured `auth.type`. Adapters and clients don't know how
  a vendor authenticates, so any vendor can use any auth strategy. Today that is an API key header, with a
  different header for each vendor. Adding OAuth2 means adding an `AuthType` and one factory bean; the clients stay
  unchanged.
- **Admins onboard devices and conversions as data, not code.** An admin registers each vendor, then each
  supported device model with its metric mappings. A mapping says how to read one vendor metric and convert it into
  the platform's standard metric and unit: `internal = external × factor + offset`. For example, a Samsung fridge's
  `temperature` in °F becomes `TEMPERATURE` in °C, and an Amazon AC's `powerConsumption` in kW becomes `POWER` in W.
  Presets such as `F_TO_C` or `WH_TO_KWH` cover the common cases, and an optional sample check catches mistakes.
  A new model or unit needs no deployment. Users then pick from this catalogue when they register their
  appliances.
- **One adapter per vendor, one standard data model behind them.** Only the adapters know each vendor's request
  and response format. Everything after them (storage, history, reports) works with normalized readings, so
  metrics compare across vendors.
- **Collection runs on each device's own interval.** Every device has `nextRunAt`. A scheduler picks up due
  devices and fetches everything since the last successful run, so a missed poll is caught up later, never lost.
  Vendor calls happen outside database transactions, and failures are retried with a delay.
- **Reports are built on daily rollups.**
  - After a home's local day ends (plus a 1 h grace period), a job writes one set of statistics per device, metric
    and day, together with that day's DAILY report, in one transaction.
  - A unique constraint makes the job idempotent. Failed days are retried with backoff.
  - On-demand reports read the rollups for full days and raw readings only for the partial days at the edges, so a
    long range stays fast.
