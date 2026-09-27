---
name: api
description: Build a production-grade Spring Boot REST API for the named resource or endpoint (e.g. "/api devices", "/api POST /reports"). Creates request/response classes (never records), validation, service, controller, ProblemDetail errors, pagination, OpenAPI docs, tests and sample requests following CLAUDE.md conventions.
argument-hint: <resource or endpoint, e.g. "devices" or "GET /devices/{id}/metrics">
---

# API skill — production-standard REST endpoints

Target: **$ARGUMENTS**

Follow `CLAUDE.md` (stack, packages, conventions). This skill adds the step-by-step recipe and checklists.

## Step 0 — Understand before writing

1. Parse the request into: resource, operations (list/get/create/update/delete/action), owning feature package.
2. Read existing code in that feature and in `common/` (error handler, paging, Clock) — reuse, don't duplicate.
3. If the domain model/schema doesn't exist yet, do that first (use the `/db-change` skill approach).
4. Write a short contract sketch before coding (method, path, request, response, status codes, errors).
   If anything is ambiguous, pick the convention from this skill and state the assumption in the summary.

## Step 1 — Contract design rules

| Operation | Method & path | Success | Typical errors |
|---|---|---|---|
| List | `GET /api/v1/smart-home/{res}?page&size&sort&filters` | 200 `PageResponse<T>` | 400 bad filter |
| Get | `GET /api/v1/smart-home/{res}/{id}` | 200 | 404 |
| Create | `POST /api/v1/smart-home/{res}` | 201 + `Location` + body | 400, 409 duplicate |
| Partial update | `PATCH /api/v1/smart-home/{res}/{id}` | 200 | 400, 404, 409 version conflict |
| Replace (rare) | `PUT /api/v1/smart-home/{res}/{id}` | 200 | 400, 404, 409 |
| Delete | `DELETE /api/v1/smart-home/{res}/{id}` | 204 (idempotent: 204 even if already gone is acceptable, or 404 — be consistent) | 404 |
| Action | `POST /api/v1/smart-home/{res}/{id}/{verb-noun}` e.g. `/collections` | 202 if async, 200/201 if sync | 404, 409, 429 |
| Async job | `POST` → 202 + `Location: /api/v1/smart-home/{res}/{id}`; poll `GET` returns `status` | 202 | 400 |

- Plural kebab-case nouns, UUID path ids, camelCase JSON, ISO-8601 UTC `Instant`s, enums as UPPER_SNAKE strings.
- Time ranges: `from` inclusive, `to` exclusive; validate `from < to` and max span.
- Never expose entities, internal ids of vendors' secrets, or stack traces.
- Filters are explicit query params (`type`, `vendor`, `room`, `enabled`), not a generic query language.

## Step 2 — Files to create (feature `X`, resource `Foo`)

```
X/api/FooController.java
X/api/dto/CreateFooRequest.java      // class + Jakarta validation
X/api/dto/UpdateFooRequest.java      // class, all fields nullable for PATCH
X/api/dto/FooResponse.java           // class + static from(Foo)
X/domain/service/FooService.java     // @Service, @Transactional boundaries, business rules
X/domain/exception/FooNotFoundException.java // extends common NotFoundException
X/domain/repository/FooRepository.java // Spring Data (if not existing)
common/api/PageResponse.java         // reuse if exists
common/api/GlobalExceptionHandler.java // reuse; add mappings only if new exception types
http/foo.http                        // sample requests
```

## Step 3 — Implementation templates

No `record` types in this project (CLAUDE.md §6) — DTOs are plain classes with `private final` fields,
one constructor (`@JsonCreator` + `@JsonProperty` for request bodies) and getters.

**Request class with validation**
```java
public final class CreateDeviceRequest {
    @NotBlank @Size(max = 100) private final String name;
    @NotNull private final DeviceType type;
    @NotNull private final Vendor vendor;
    @NotBlank @Size(max = 100) private final String externalDeviceId;
    @Size(max = 50) private final String room;
    @Min(10) @Max(86_400) private final Integer collectionIntervalSeconds;

    @JsonCreator
    public CreateDeviceRequest(@JsonProperty("name") String name, @JsonProperty("type") DeviceType type,
            @JsonProperty("vendor") Vendor vendor, @JsonProperty("externalDeviceId") String externalDeviceId,
            @JsonProperty("room") String room, @JsonProperty("collectionIntervalSeconds") Integer collectionIntervalSeconds) {
        this.name = name; this.type = type; this.vendor = vendor;
        this.externalDeviceId = externalDeviceId; this.room = room; this.collectionIntervalSeconds = collectionIntervalSeconds;
    }

    public String getName() { return name; }
    // ... one getter per field
}
```

**Response class**
```java
public final class DeviceResponse {
    private final UUID id;
    private final String name;
    // ... type, vendor, externalDeviceId, room, collectionIntervalSeconds, enabled,
    //     lastCollectedAt, nextCollectionAt, createdAt, updatedAt, version

    private DeviceResponse(Device a) { this.id = a.getId(); this.name = a.getName(); /* ... */ }

    public static DeviceResponse from(Device a) { return new DeviceResponse(a); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    // ... one getter per field
}
```

**Controller** — thin: validate, delegate, map, set status/headers.
```java
@RestController
@RequestMapping("/api/v1/smart-home/devices")
@RequiredArgsConstructor
@Tag(name = "Devices")                       // springdoc, once added
class DeviceController {
    private final DeviceService service;

    @PostMapping
    ResponseEntity<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest req, UriComponentsBuilder uri) {
        var created = DeviceResponse.from(service.register(req.toCommand()));
        return ResponseEntity.created(uri.path("/api/v1/smart-home/devices/{id}").build(created.getId())).body(created);
    }

    @GetMapping
    PageResponse<DeviceResponse> list(@RequestParam(required = false) DeviceType type,
                                         @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.list(type, cap(pageable)).map(DeviceResponse::from));
    }
}
```

**Service** — business rules, transactions, `Clock`.
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {
    private final DeviceRepository repository;
    private final Clock clock;

    @Transactional
    public Device register(RegisterDeviceCommand cmd) {
        if (repository.existsByVendorAndExternalDeviceId(cmd.getVendor(), cmd.getExternalDeviceId())) {
            throw new ConflictException("Device already registered for vendor device " + cmd.getExternalDeviceId());
        }
        return repository.save(Device.register(cmd, clock.instant()));
    }

    public Device get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DeviceNotFoundException(id));
    }
}
```

**Paging DTO** (stable JSON, avoids serializing `PageImpl`)
```java
public final class PageResponse<T> {
    private final List<T> content;
    private final PageMeta page;

    private PageResponse(List<T> content, PageMeta page) { this.content = content; this.page = page; }

    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(p.getContent(),
                new PageMeta(p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()));
    }

    public List<T> getContent() { return content; }
    public PageMeta getPage() { return page; }

    public static final class PageMeta {
        private final int number, size, totalPages;
        private final long totalElements;
        PageMeta(int number, int size, long totalElements, int totalPages) {
            this.number = number; this.size = size; this.totalElements = totalElements; this.totalPages = totalPages;
        }
        public int getNumber() { return number; }
        public int getSize() { return size; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
    }
}
```
Cap `size` at 100 (or set `spring.data.web.pageable.max-page-size: 100`).

**Errors** — one `@RestControllerAdvice extends ResponseEntityExceptionHandler` returning `ProblemDetail`:

| Exception | Status | `type` suffix |
|---|---|---|
| `NotFoundException` | 404 | `/problems/not-found` |
| `ConflictException`, `OptimisticLockingFailureException` | 409 | `/problems/conflict` |
| `MethodArgumentNotValidException`, `ConstraintViolationException`, bad enum/UUID | 400 | `/problems/validation` + `errors: [{field, message}]` |
| `VendorRateLimitedException` | 429 + `Retry-After` | `/problems/vendor-rate-limited` |
| `VendorUnavailableException` | 502/503 | `/problems/vendor-unavailable` |
| anything else | 500, generic message, log with error id | `/problems/internal` |

Enable `spring.mvc.problemdetails.enabled: true`. Always set `title`, `detail`, `instance`; add `errorId` for 500s.

## Step 4 — Production concerns checklist

- [ ] Input validated (body `@Valid`, path/query `@Validated` on controller + constraints).
- [ ] Correct status codes & `Location` headers; no 200-with-error-body.
- [ ] Pagination bounded; sort whitelisted (reject unknown sort properties → 400).
- [ ] Optimistic locking (`@Version`) surfaces as 409.
- [ ] Idempotency: create endpoints reject duplicates on natural key (409); async `POST` accepts optional `Idempotency-Key`.
- [ ] Transactions in service; no vendor calls inside DB transactions.
- [ ] N+1 avoided (fetch joins / projections for list endpoints).
- [ ] No entity leakage; no sensitive fields in responses or logs.
- [ ] Structured logging at INFO for state changes, DEBUG for details; MDC keys set.
- [ ] OpenAPI: `@Operation(summary)`, `@ApiResponse` for non-2xx (once springdoc is added).
- [ ] Time via injected `Clock`.
- [ ] Backwards compatible: additive changes only under `/v1`.

## Step 5 — Tests (use the `/test` skill conventions)

Minimum per endpoint:
1. `@WebMvcTest(FooController.class)` + `@MockitoBean FooService`: happy path (status, headers, JSON via
   `jsonPath`), validation 400 with field errors, 404, 409.
2. Service unit test (Mockito, fixed `Clock`): business rules and exceptions.
3. One integration test `FooApiIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + real DB) covering the
   create → get → list → update → delete flow.

## Step 6 — Docs & samples

- Add requests to `http/<resource>.http` (happy path + one error case each).
- Update README "API walkthrough" with curl examples.

## Step 7 — Verify & report

Run `./mvnw test` (and `./mvnw verify` if ITs exist). Report back:
- Endpoint table (method, path, status codes).
- Files created/changed.
- Test results.
- Assumptions made.
