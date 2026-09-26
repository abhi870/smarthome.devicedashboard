---
name: scheduled-job
description: Add or change a background or scheduled job (per-appliance metric collection dispatcher, daily report job, on-demand async report, retention purge) that is configurable, idempotent, observable and deterministic to test.
argument-hint: <job, e.g. "daily report job" or "collection dispatcher backoff">
---

# Scheduled / background job skill

Target: **$ARGUMENTS**

## Principles

1. **Configurable**: cadence and limits in `@ConfigurationProperties` (`smarthome.collection.*`, `smarthome.report.*`),
   never hard-coded. Use `@Scheduled(fixedDelayString = "${smarthome.collection.tick:5s}")` or `cron = "${...}"` + `zone`.
2. **Thin trigger, fat service**: the `@Scheduled` method only calls a service method (e.g. `dispatcher.dispatchDue()`),
   so tests call the service directly with a controlled `Clock` — no waiting for the scheduler.
3. **Idempotent & restart-safe**: state lives in the DB (`nextCollectionAt`, report unique key, status columns).
   Re-running a job for the same window must not duplicate data.
4. **Bounded concurrency**: dedicated `ThreadPoolTaskExecutor` bean per job type (core/max/queue from properties,
   `CallerRunsPolicy` or skip-with-log when saturated). Don't use the default scheduler thread for I/O work.
5. **Isolation of failures**: one appliance/vendor failing must not stop others; catch per item, record outcome.
6. **Transactions**: fetch due work in a short tx → do vendor I/O outside tx → persist results in a short tx.
7. **Observability**: log start/end with counts and duration; record `CollectionRun` rows; Micrometer counters/timers
   (`smarthome.collection.runs{vendor,status}`) once actuator is present.
8. **Multi-instance** (optional): ShedLock `@SchedulerLock(name=..., lockAtMostFor=...)`.
9. Enable scheduling in one `@Configuration` with `@EnableScheduling`, and allow disabling via
   `smarthome.scheduling.enabled=false` (tests disable it and drive services directly).

## Patterns for this project

**Collection dispatcher**
```
tick → find enabled appliances with nextCollectionAt <= now (limit batch)
     → mark as in-progress (set nextCollectionAt = now + interval) to avoid double pick-up
     → submit each to executor → adapter.fetchMetrics → save readings (dedupe) → CollectionRun(status)
     → on failure: nextCollectionAt = now + min(interval * 2^failures, maxBackoff); on 429 honour retry-after
```
Manual trigger `POST /api/v1/appliances/{id}/collections` reuses the same `CollectionService.collect(id)`.

**Daily report job**
```
cron (default 00:05 in smarthome.report.zone) → window = [yesterday 00:00, today 00:00) in that zone → to UTC
→ if report(DAILY, window, ALL) exists and COMPLETED → skip; else generate via ReportService (same code as on-demand)
```
Allow backfill: `POST /api/v1/reports` with `type=DAILY&date=YYYY-MM-DD` or an admin method.

**On-demand report (async)**
```
POST /reports → validate range → persist PENDING → return 202 + Location
→ @Async / executor: RUNNING → aggregate → COMPLETED(payload) | FAILED(error)
```
Use `TransactionalEventListener(phase = AFTER_COMMIT)` or submit after save so the worker sees the row.

## Tests

- Unit: dispatcher selects due appliances only; backoff math; daily window computation around timezone/DST boundaries
  (parameterized); report idempotency.
- Integration: scheduling disabled; call `dispatcher.dispatchDue()` with a `MutableClock`, advance time, assert
  readings + `CollectionRun` rows; async report reaches COMPLETED via Awaitility.
- One smoke IT with scheduling enabled and a short tick to prove wiring (Awaitility, ≤ 10s).

## Done when

Properties documented in `application.yaml` with defaults, job can be disabled, tests green, summary states cadence,
idempotency key and failure behavior.
