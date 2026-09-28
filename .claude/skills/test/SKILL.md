---
name: test
description: Write or improve tests for a class, feature or endpoint using current Spring Boot 4 / JUnit 5 practices — unit tests, @WebMvcTest/@DataJpaTest slices, Testcontainers integration tests, scheduled-job and vendor-adapter tests. Use when asked to add tests, raise coverage, or fix flaky tests.
argument-hint: <class, feature or endpoint to test, e.g. "DeviceService" or "report feature">
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
- Names: `shouldReturn404_whenDeviceDoesNotExist()`; optional `@DisplayName("…")`. Group with `@Nested`.
- AssertJ only (`assertThat`, `assertThatThrownBy`, `.extracting`, `.usingRecursiveComparison()`); no JUnit `assertEquals`.
- `@MockitoBean` / `@MockitoSpyBean` (the old `@MockBean`/`@SpyBean` are removed in Boot 4).
- Package-private test classes and methods (`class FooTest`, `void should…()`).
- **Time**: entity methods take `now` as a parameter, so test them with fixed instants
  (`Instant.parse("2026-01-15T10:00:00Z")`). Services call `Instant.now()`; assert those values with
  `before = Instant.now(); ...; after = Instant.now(); assertThat(x).isBetween(before, after)` or capture the argument.
- **Async**: no `Thread.sleep`. Use Awaitility `await().atMost(5, SECONDS).untilAsserted(...)`.
- **Randomness**: mock vendors take a seed / failure-mode config; tests set it explicitly.
- **Isolation**: each test creates its own data (test data builders); no reliance on seed data or order.
  Clean DB between ITs (`@Sql` truncate script or repository `deleteAll` in `@AfterEach`).
- Parameterize edge cases with `@ParameterizedTest` + `@CsvSource`/`@MethodSource`/`@EnumSource`.
- Imports: Boot 4 moved slice annotations into module packages — e.g.
  `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. Let the compiler confirm; don't use Boot 3 packages.

## Step 2 — Test data builders

Create/extend `src/test/java/.../support/TestData.java` (or per-feature `DeviceFixtures`):
```java
public final class DeviceFixtures {
    public static Device.DeviceBuilder anDevice() {
        return Device.builder().name("Living Room AC").type(DeviceType.AIR_CONDITIONER)
                .vendor(VendorCode.SAMSUNG).externalDeviceId("sam-" + UUID.randomUUID()).collectionIntervalSeconds(60).enabled(true);
    }
}
```

## Step 3 — Templates

**Unit (service)**
```java
@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    @Mock DeviceRepository repository;
    DeviceService service;

    @BeforeEach void setUp() { service = new DeviceService(repository); }

    @Test
    void shouldRejectDuplicateVendorDevice() {
        // given
        given(repository.existsByVendorAndExternalDeviceId(VendorCode.SAMSUNG, "d-1")).willReturn(true);
        // when / then
        assertThatThrownBy(() -> service.register(command("d-1")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("d-1");
        then(repository).should(never()).save(any());
    }
}
```

**Web slice (AssertJ-style MockMvcTester)**
```java
@WebMvcTest(DeviceController.class)
@Import(GlobalExceptionHandler.class)
class DeviceControllerTest {
    @Autowired MockMvcTester mvc;
    @MockitoBean DeviceService service;

    @Test
    void shouldReturn400WithFieldErrors_whenNameMissing() {
        assertThat(mvc.post().uri("/api/v1/smart-home/devices").contentType(APPLICATION_JSON)
                .content("""
                        {"type":"OVEN","vendor":"SAMSUNG","externalDeviceId":"x"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }
}
```
Check: status, no `Location` header, content type (`application/problem+json` for errors), JSON fields, and that the
service was (or was not) called. Services return response DTOs, so stub them with `FooResponse.from(entity)`.
For fetch behaviour (no N+1, limit applied in SQL) assert Hibernate `Statistics.getPrepareStatementCount()` in a
`@DataJpaTest` that `@Import`s the services (see `HomeDeviceServiceQueryCountTest`).

## Step 4 — Domain-specific must-cover cases

- **Collection**: due vs not-due devices; disabled skipped; `nextCollectionAt` advanced by interval; backoff on failure;
  interval change takes effect; one vendor failing doesn't block others; `CollectionRun` recorded per status.
- **Vendor adapters**: metric name/unit mapping (e.g. °F → °C, Wh → kWh), unknown metrics dropped,
  429 → `VendorRateLimitedException`, 5xx/timeout → `VendorUnavailableException`, auth refresh path.
- **Metrics history**: idempotent insert (duplicate reading ignored), range queries half-open, pagination.
- **Reports**: min/max/avg/count correctness on a hand-computed dataset, empty range, device with no data,
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
