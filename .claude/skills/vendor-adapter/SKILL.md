---
name: vendor-adapter
description: Add or change a mocked appliance vendor integration — a VendorAdapter with its own auth style, payload shape, metric names/units, rate limits and failure modes, normalized to canonical MetricType. Use when adding a vendor, a new metric, or changing how vendor data is mapped.
argument-hint: <vendor name and traits, e.g. "Globex: OAuth, Fahrenheit, 30 req/min">
---

# Vendor adapter skill

Target: **$ARGUMENTS**

Vendors are mocked in-process but must behave like real, imperfect third-party APIs so the normalization,
resilience and reporting logic is actually exercised.

## Contract (package `vendor`)

```java
public interface VendorAdapter {
    Vendor vendor();
    Set<MetricType> capabilities(ApplianceType type);
    List<MetricReading> fetchMetrics(ApplianceRef appliance);   // normalized; throws Vendor*Exception
}
```
- `VendorAdapterRegistry` maps `Vendor → VendorAdapter` (inject `List<VendorAdapter>`, fail fast on duplicates).
- `ApplianceRef` = (applianceId, vendorDeviceId, type) — adapters never see JPA entities.
- Exceptions: `VendorRateLimitedException(retryAfter)`, `VendorUnavailableException`, `VendorAuthException`, `UnknownDeviceException`.

## Recipe

1. **Model the vendor's raw API** in `vendor/mock/<name>/`:
   - `<Name>Client` — the fake remote (simulates latency, auth, rate limit, errors) returning **raw vendor DTOs**
     (records mirroring their payload: their field names, units, timestamps, nesting).
   - `<Name>Adapter implements VendorAdapter` — calls the client, handles auth, maps raw → `MetricReading`.
   - `<Name>Properties` (`@ConfigurationProperties("smarthome.vendors.<name>")`): base latency, failure rate,
     rate limit, credentials placeholder, random seed.
2. **Give it distinct traits** (pick what the request says; otherwise vary from existing vendors):
   - Auth: static API key header, OAuth-style expiring token with refresh, signed request.
   - Shape: flat JSON map, nested `{"sensors":[{"id":"t1","val":..}]}`, CSV-ish string, batch endpoint.
   - Naming/units: `pwr` in W, `energy_wh` (Wh → kWh), `temp_f` (°F → °C), epoch-seconds vs ISO timestamps.
   - Capabilities per appliance type (a TV has no temperature; a fridge has door-open count).
   - Limits/reliability: token-bucket N req/min → 429 with retry-after; configurable 5xx rate; latency jitter; occasional stale/duplicate readings.
3. **Normalize** in a dedicated `<Name>MetricMapper` (pure function, easy to unit test): name → `MetricType`,
   unit conversion, timestamp → `Instant` UTC. Unknown metrics: `log.debug` + drop. Invalid values (NaN, negative energy): drop + count.
4. **Resilience**: wrap client calls with timeout + limited retry on transient errors only (not 4xx/auth),
   honour retry-after, respect vendor rate limit client-side (simple per-vendor token bucket or Spring 7 `@ConcurrencyLimit`).
   Never retry inside a DB transaction.
5. **Determinism**: all randomness from a `Random` seeded via properties; tests set seed & failure rate (0 or 1).
6. **Register metadata**: expose via `GET /api/v1/vendors` (name, auth style, supported types/metrics, rate limit) if that endpoint exists.
7. **Seed**: add a sample appliance for the vendor in dev seed data.

## Tests (see `/test`)

- Mapper unit tests: every mapping incl. unit conversions (parameterized), unknown metric dropped.
- Adapter unit tests: success, 429 → `VendorRateLimitedException` with retry-after, 5xx → unavailable after retries,
  token expiry → refresh then success, unsupported appliance type → empty capabilities.
- Registry test: every `Vendor` enum value has exactly one adapter.

## Done when

Adapter registered, config documented in `application.yaml`, seed appliance added, tests green, summary lists
the vendor's traits and the mapping table (vendor metric → canonical metric, unit conversion).
