---
name: test
description: Write or improve tests for a class, feature or endpoint using current Spring Boot 4 / JUnit 5 practices — unit tests, @WebMvcTest/@DataJpaTest slices, Testcontainers integration tests, scheduled-job and vendor-adapter tests. Use when asked to add tests, raise coverage, or fix flaky tests.
argument-hint: <class, feature or endpoint to test, e.g. "ApplianceService" or "report feature">
---

# Test skill — modern Spring Boot testing

Target: **$ARGUMENTS**

Follow `CLAUDE.md`. Stack: JUnit Jupiter, AssertJ, Mockito, Spring Boot 4.1 test slices
(`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`), MockMvc / `MockMvcTester`,
Testcontainers 2 (when added), Awaitility (when added).

## Step 0 — Decide what to test and at which level

Read the target code and list its **behaviors** (not methods): happy paths, validation, edge cases
(empty, boundaries, time windows), failure modes (vendor errors, not found, conflicts), concurrency/idempotency.
Then pick the **cheapest level** that proves each behavior:

| Level | When | Tooling | Naming |
|---|---|---|---|
| Unit | Pure logic: services, mappers, normalizers, report aggregation, scheduling math | JUnit + Mockito + AssertJ, **no Spring context** | `FooTest` |
| Web slice | HTTP contract: status, headers, JSON shape, validation, error mapping | `@WebMvcTest(FooController.class)` + `@MockitoBean` | `FooControllerTest` |
| JPA slice | Custom queries, constraints, aggregation SQL, mappings | `@DataJpaTest` (+ Testcontainers Postgres for Postgres-specific SQL) | `FooRepositoryTest` |
| Integration | End-to-end workflows across features, scheduling, async | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + real DB | `FooIT` (run by failsafe in `verify`) |
| Architecture | Package rules from CLAUDE.md §4 | ArchUnit | `ArchitectureTest` |

Aim: many unit tests, focused slices, few but meaningful ITs.

## Step 1 — Conventions (non-negotiable)

- Structure: `// given` / `// when` / `// then` blocks. One behavior per test.
- Names: `shouldReturn404_whenApplianceDoesNotExist()`; optional `@DisplayName("…")`. Group with `@Nested`.
- AssertJ only (`assertThat`, `assertThatThrownBy`, `.extracting`, `.usingRecursiveComparison()`); no JUnit `assertEquals`.
- `@MockitoBean` / `@MockitoSpyBean` (the old `@MockBean`/`@SpyBean` are removed in Boot 4).
- Package-private test classes and methods (`class FooTest`, `void should…()`).
- **Time**: never real time. Use a fixed `Clock` (`Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC)`)
  or a small `MutableClock` test helper to advance time for scheduling tests.
- **Async**: no `Thread.sleep`. Use Awaitility `await().atMost(5, SECONDS).untilAsserted(...)`.
- **Randomness**: mock vendors take a seed / failure-mode config; tests set it explicitly.
- **Isolation**: each test creates its own data (test data builders); no reliance on seed data or order.
  Clean DB between ITs (`@Sql` truncate script or repository `deleteAll` in `@AfterEach`).
- Parameterize edge cases with `@ParameterizedTest` + `@CsvSource`/`@MethodSource`/`@EnumSource`.
- Imports: Boot 4 moved slice annotations into module packages — e.g.
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. Let the compiler confirm; don't use Boot 3 packages.

## Step 2 — Test data builders

Create/extend `src/test/java/.../support/TestData.java` (or per-feature `ApplianceFixtures`):
```java
public final class ApplianceFixtures {
    public static Appliance.ApplianceBuilder anAppliance() {
        return Appliance.builder().name("Living Room AC").type(ApplianceType.AIR_CONDITIONER)
                .vendor(Vendor.ACME).vendorDeviceId("acme-" + UUID.randomUUID()).collectionIntervalSeconds(60).enabled(true);
    }
}
```

## Step 3 — Templates

**Unit (service)**
```java
@ExtendWith(MockitoExtension.class)
class ApplianceServiceTest {
    @Mock ApplianceRepository repository;
    Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    ApplianceService service;

    @BeforeEach void setUp() { service = new ApplianceService(repository, clock); }

    @Test
    void shouldRejectDuplicateVendorDevice() {
        // given
        given(repository.existsByVendorAndVendorDeviceId(Vendor.ACME, "d-1")).willReturn(true);
        // when / then
        assertThatThrownBy(() -> service.register(command("d-1")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("d-1");
        then(repository).should(never()).save(any());
    }
}
```

**Web slice (AssertJ-style MockMvcTester)**
```java
@WebMvcTest(ApplianceController.class)
@Import(GlobalExceptionHandler.class)
class ApplianceControllerTest {
    @Autowired MockMvcTester mvc;
    @MockitoBean ApplianceService service;

    @Test
    void shouldReturn400WithFieldErrors_whenNameMissing() {
        assertThat(mvc.post().uri("/api/v1/appliances").contentType(APPLICATION_JSON)
                .content("""
                        {"type":"OVEN","vendor":"ACME","vendorDeviceId":"x"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }
}
```
Check: status, `Location`, content type (`application/problem+json` for errors), JSON fields, and that the
service was (or was not) called.

**JPA slice**
```java
@DataJpaTest
class MetricReadingRepositoryTest {
    @Autowired MetricReadingRepository repository;
    @Autowired TestEntityManager em;
    @Test void shouldAggregateOnlyWithinHalfOpenRange() { /* boundary readings at from and to */ }
}
```
Add `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers when testing Postgres-specific SQL.

**Integration with Testcontainers (once dependencies are added)**
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {
    @Bean @ServiceConnection
    PostgreSQLContainer postgres() { return new PostgreSQLContainer("postgres:17-alpine"); }
}

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import({TestcontainersConfig.class, TestClockConfig.class})
class CollectionWorkflowIT {
    @Autowired RestTestClient client;   // or TestRestTemplate
    @Test void shouldCollectMetricsAndProduceReport() {
        // register appliance → trigger POST /appliances/{id}/collections → await metrics → POST /reports → poll until COMPLETED → assert aggregates
    }
}
```
Reuse one container per JVM (static bean / `@ImportTestcontainers`) to keep ITs fast. If Docker is unavailable,
fall back to H2 and state that in the summary.

## Step 4 — Domain-specific must-cover cases

- **Collection**: due vs not-due appliances; disabled skipped; `nextCollectionAt` advanced by interval; backoff on failure;
  interval change takes effect; one vendor failing doesn't block others; `CollectionRun` recorded per status.
- **Vendor adapters**: metric name/unit mapping (e.g. °F → °C, Wh → kWh), unknown metrics dropped,
  429 → `VendorRateLimitedException`, 5xx/timeout → `VendorUnavailableException`, auth refresh path.
- **Metrics history**: idempotent insert (duplicate reading ignored), range queries half-open, pagination.
- **Reports**: min/max/avg/count correctness on a hand-computed dataset, empty range, appliance with no data,
  timezone day boundaries for daily report, idempotent daily job, async status transitions, CSV output shape.
- **API**: every documented error status is tested at least once.

## Step 5 — Run, measure, report

```bash
./mvnw test                  # unit + slices
./mvnw verify                # + *IT
```
If JaCoCo is configured, check `target/site/jacoco/index.html` (goal ≥ 80% lines on domain/vendor/report).
Report: tests added (by level), behaviors covered, any gaps or flaky risks, final pass/fail counts.
Never weaken assertions or `@Disabled` a test to make the build green — fix the cause or report it.
