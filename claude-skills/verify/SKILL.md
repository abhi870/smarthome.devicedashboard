---
name: verify
description: Verify the backend end to end before declaring work done or submitting — build, run all tests, start the app, smoke-test the register → collect → history → report workflow with curl, and summarize results and gaps.
argument-hint: "[optional focus, e.g. 'reports only']"
---

# Verify skill — reviewer's path, automated

Focus: **$ARGUMENTS** (default: full workflow)

## 1. Static & build

```bash
./mvnw -q clean verify
```
Collect: compile warnings, test counts (surefire + failsafe), failures. If anything fails, stop and fix or report —
do not skip tests.

## 2. Run the app

Prefer Postgres (Docker) → `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
If Docker is unavailable → `-Dspring-boot.run.profiles=dev,h2`.
Run in background, wait for `GET /actuator/health` (or any GET) to succeed (poll up to 60s).

## 3. Smoke-test the workflow (curl, base `http://localhost:8080/api/v1`)

1. `POST /appliances` for two vendors → 201 + `Location`; capture ids.
2. `GET /appliances?page=0&size=10` → both present.
3. `PATCH /appliances/{id}` change `collectionIntervalSeconds` → 200, version incremented.
4. `POST /appliances/{id}/collections` → readings stored; `GET /appliances/{id}/collections` shows a `CollectionRun`.
5. Wait ≥ one interval (use a short interval like 10s) → `GET /appliances/{id}/metrics?from=&to=` shows scheduled readings.
6. `POST /reports` with a custom range → 202 + `Location`; poll until `COMPLETED`; check aggregates non-empty.
7. `GET /reports/{id}` with `Accept: text/csv` → CSV.
8. Trigger/inspect a daily report (backfill endpoint or seed) → exists for yesterday.
9. Error cases: invalid body → 400 problem+json; unknown id → 404; duplicate vendor device → 409; `from >= to` → 400.
10. Flaky vendor: confirm a FAILED/RATE_LIMITED `CollectionRun` doesn't stop other appliances.

Stop the app afterwards.

## 4. Docs check

- README has: prerequisites, run commands (Docker and no-Docker), API walkthrough, how to run tests, design notes.
- `http/*.http` matches actual endpoints. CLAUDE.md stack table matches `pom.xml`.

## 5. Report

Return a concise table: step → expected → actual → ✅/❌, plus test totals, anything skipped (e.g. no Docker),
and recommended fixes. Don't claim success for steps that weren't actually run.
