---
name: db-change
description: Add or change JPA entities, repositories, indexes and schema safely for Postgres and H2 (and Flyway migrations once enabled). Use for new tables, columns, constraints, time-series metric queries or report aggregation SQL.
argument-hint: <schema change, e.g. "add collection_run table">
---

# DB change skill

Target: **$ARGUMENTS**

## Rules

- Schema is owned by **migrations**, not Hibernate. Until Flyway is added, use `ddl-auto: validate` in Postgres
  profile only after migrations exist; for the very first iteration `update` is tolerated in `h2`/`dev` — say so in the summary.
  Preferred: add `spring-boot-starter-flyway` + `flyway-database-postgresql` and write
  `src/main/resources/db/migration/V<n>__<snake_description>.sql`. **Never edit an applied migration** — add a new one.
- SQL must run on **Postgres and H2** (use `MODE=PostgreSQL` for H2 URL). Avoid vendor-specific types; `uuid`,
  `timestamp with time zone`, `varchar`, `double precision`, `bigint`, `boolean`, `text` are safe.
- Every table: PK, `created_at`, and for mutable aggregates `updated_at` + `version bigint not null default 0`.
- Name constraints/indexes explicitly: `pk_`, `fk_`, `uk_`, `ix_`.
- Enums stored as `varchar` + `@Enumerated(EnumType.STRING)` (never ORDINAL).
- Times stored as UTC `Instant` (`timestamp with time zone`); set `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`.

## Entity template

```java
@Entity
@Table(name = "device", uniqueConstraints = @UniqueConstraint(name = "uk_device_vendor_device",
        columnNames = {"vendor", "vendor_device_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DeviceType type;
    // ...
    @Version private long version;

    public static Device register(RegisterDeviceCommand cmd, Instant now) { /* factory, sets id = UUID.randomUUID() */ }
    public void changeInterval(int seconds, Instant now) { /* invariant checks, reschedule */ }

    @Override public boolean equals(Object o) { return o instanceof Device a && id != null && id.equals(a.id); }
    @Override public int hashCode() { return getClass().hashCode(); }
}
```
No `@Data`, no public setters; behavior methods enforce invariants. Assign UUIDs in the factory (no DB round-trip).

## Time-series metrics guidance

- `metric_reading(id bigint identity, device_id uuid, metric_type varchar, value double precision, unit varchar,
  recorded_at timestamptz, collected_at timestamptz)`, `uk_metric_reading (device_id, metric_type, recorded_at)`,
  `ix_metric_reading_lookup (device_id, recorded_at)`.
- No JPA relationship from `DeviceReading` to `HomeDevice` (just `homeDeviceId`) — avoids loading graphs on hot paths.
- Batch inserts: `hibernate.jdbc.batch_size: 50`, `order_inserts: true`; identity PK disables batching in Hibernate,
  so prefer a `SEQUENCE` with `allocationSize = 50` if volume matters.
- Aggregations for reports via a JPQL/native **projection** query grouped by device + metric
  (`min, max, avg, count, sum`) over `recorded_at >= :from and recorded_at < :to` — not by loading rows into memory.
- Retention (optional): configurable `smarthome.metrics.retention-days` purge job.

## Repository rules

- Spring Data interfaces in `repository/`; entities in `entity/`; enums in `enums/`; derived queries for simple cases, `@Query` for the rest; projections as interfaces or classes (no records).
- List endpoints: `Page<T>` with bounded size; avoid N+1 (`@EntityGraph` or fetch join).
- Postgres-only SQL (e.g. `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`) only behind a `repository` class with an H2-safe alternative or a Testcontainers-only test.

## Tests

`@DataJpaTest` per repository with custom queries: constraints (duplicate → `DataIntegrityViolationException`),
range boundaries, aggregation correctness on hand-computed data, optimistic lock conflict.

## Done when

Migration (or documented ddl strategy), entity, repository, tests green on H2 (and Postgres via Testcontainers if configured);
summary lists tables/columns/indexes changed.
