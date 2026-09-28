# Testing plan: onboarding a home and its devices

A manual test plan for the three onboarding flows, run with the Postman collection
(`postman/smart-home.postman_collection.json`), with the demo scripts in `scripts/demo/` as an alternative.

1. **Register the home** on the platform (user).
2. **Register vendor devices** on the platform (admin): the vendor, then its supported device models with metric
   mappings.
3. **Register a home device** in the home (user): one of their appliances, as one of the supported models.

The admin flow (2) has to run before a user can register a device (3), so the steps below go vendor → catalogue →
home → home device.

## 0. Setup

| # | Step | Expected |
|---|---|---|
| 0.1 | Start the mock vendor service ([smarthome.vendors](https://github.com/abhi870/smarthome.vendors)): `./mvnw spring-boot:run` | Log shows `Started VendorsApplication` on port 8081 |
| 0.2 | Start this backend on a **fresh database**: `docker compose down -v`, then `./mvnw spring-boot:run` | Log shows Flyway migrating to V5 and `Started SmartHomeApplication` on port 8080 |
| 0.3 | Postman: **Import** `postman/smart-home.postman_collection.json` (replace the old copy if asked) | Collection "Smart Home - register devices" with folders 0–7; `baseUrl` = `http://localhost:8080` |
| 0.4 | Run **0. What's already there** | All lists return `[]` (200). On a non-empty database this fills the id variables instead, so later steps use existing data |

Every request in the collection has a test script checking the status code, and the register requests save the
returned ids into collection variables (`amazonId`, `amzAcDeviceId`, `homeId`, `amzAcHomeDeviceId`, …), which later
requests use. So you can run folders one at a time, or folders 1–4 in one go with the **Collection Runner**.

## 1. Register vendor devices on the platform (admin)

### 1a. Register the vendors — folder **1. Vendors**

| # | Request | Body | Expected |
|---|---|---|---|
| 1.1 | Register Samsung | `{"code":"SAMSUNG","name":"Samsung"}` | `201`, body has `id`, `code: SAMSUNG`; `samsungId` saved |
| 1.2 | Register Amazon | `{"code":"AMAZON","name":"Amazon"}` | `201`; `amazonId` saved |
| 1.3 | Register Cisco | `{"code":"CISCO","name":"Cisco"}` | `201`; `ciscoId` saved |

Negative checks (send by hand):

| # | Change | Expected |
|---|---|---|
| 1.4 | Send **Register Amazon** again | `409` "Request conflicts with existing data" (vendor codes are unique) |
| 1.5 | `"code": "VENDOR_B"` | `400` (only SAMSUNG, AMAZON, CISCO are integrated) |

### 1b. Add the supported device models with metric mappings — folder **2. Catalogue devices (admin)**

| # | Request | Expected |
|---|---|---|
| 1.6 | Samsung Family Hub Fridge, Samsung EcoBubble Washer | `201` each; mappings use dotted paths (e.g. `powerConsumptionReport.energy`) because Samsung payloads are nested |
| 1.7 | Amazon Fire TV 55, **Amazon Smart AC 12K** | `201`; for the AC check `mappings`: `powerConsumption → POWER` with `externalUnit: kW`, `internalUnit: W`, `factor: 1000` |
| 1.8 | Cisco Smart Oven 20L, Cisco Heat-Pump Dryer | `201`; `state → SWITCH` |
| 1.9 | **0. What's already there → List catalogue devices** | 6 devices, each with its `vendorCode` and `mappings` |

Negative checks:

| # | Request | Expected |
|---|---|---|
| 1.10 | Send **Amazon Smart AC 12K** again | `409` (model is unique per vendor) |
| 1.11 | **5. Read & update → Invalid mapping (internalUnit kW for POWER)** | `400` "must be 'W' for metric POWER" — stored units are always the metric's canonical unit |
| 1.12 | Register a device with `"mappings": []` | `400` with a field error on `mappings` |
| 1.13 | **5. Read & update → Replace mappings (Amazon AC)** | `200`, the new mappings are returned; they apply from the next collection |

## 2. Register the home on the platform (user) — folder **3. Home**

| # | Request | Body | Expected |
|---|---|---|---|
| 2.1 | Register home | `{"name":"My home","timezone":"Asia/Kolkata"}` | `201`, body has `id`, `timezone: Asia/Kolkata`, `createdAt` in UTC (`…Z`); `homeId` saved |
| 2.2 | **5. Read & update → Get home** | | `200`, same home |

Negative / default checks:

| # | Body | Expected |
|---|---|---|
| 2.3 | `{}` | `400`, field error on `name` |
| 2.4 | `{"name":"Second home"}` | `201`, `timezone: UTC` (default) |
| 2.5 | `{"name":"My home","timezone":"Mars/Base"}` (also `asia/kolkata` — ids are case-sensitive) | `400`, field error on `timezone`: "must be a valid IANA time zone id" |

The timezone decides where the home's report days start and end (a DAILY report for Kolkata covers 18:30Z → 18:30Z).

## 3. Register a home device in the home (user) — folder **4. Home devices (user)**

| # | Request | Expected |
|---|---|---|
| 3.1 | **Bedroom AC (amz-ac-01)**: `homeId`, `deviceId` = the AC model, `externalDeviceId: amz-ac-01`, `pollingIntervalSeconds: 60` | `201`; body shows `vendorCode: AMAZON`, `model: AZ-AC12`, `enabled: true`, `nextRunAt = createdAt` (first collection is due immediately), `lastRunAt: null`; `amzAcHomeDeviceId` saved |
| 3.2 | The other five (fridge, washer, TV, oven, dryer) | `201` each |
| 3.3 | **0. What's already there → List home devices of my home** | 6 home devices |

Negative checks:

| # | Change | Expected |
|---|---|---|
| 3.4 | Send **Bedroom AC** again | `409` (the same vendor device can't be registered twice for a model) |
| 3.5 | `deviceId` = a random UUID | `404` "Supported device … not found" |
| 3.6 | `homeId` = a random UUID | `404` "Home … not found" |
| 3.7 | `{}` | `400` with 4 field errors (`homeId`, `deviceId`, `externalDeviceId`, `name`) |
| 3.8 | **5. Read & update → Change polling interval (Bedroom AC → 15 min)**, then send `{"pollingIntervalSeconds": 10}` | `200` with the new `nextRunAt`; then `400` (allowed range 60–86400 s) |

## 4. Verify the device is collecting

| # | Step | Expected |
|---|---|---|
| 4.1 | Wait ~1 minute, then **0. → List home devices (all)** | `lastRunAt` is set and `nextRunAt` = `lastRunAt` + interval for each device |
| 4.2 | Backend log | `Collected n/n due home devices` lines and no `Collection failed for …` warnings; vendors service log shows one `GET /api/v1/<vendor>/devices/<id>/metrics… -> 200` per device per interval |
| 4.3 | **6. Device readings → All readings today** | `200`, readings in canonical units (`W`, `kWh`, `C`, `on/off`) for the AC |
| 4.4 | **7. Reports → Create on-demand report (last 24 h, all metrics)** | `201`, `status: SUCCEEDED`, one summary per mapped metric with `sampleCount > 0` |

If 4.2 shows `401` from the vendors service, the API key in `application.yaml` doesn't match the vendor's; if it
shows `404`, the `externalDeviceId` is not a device the vendors service knows (`amz-ac-01`, `sam-fridge-01`, …).

## Alternative: the demo scripts

`scripts/demo/` runs the same three flows from a terminal with `curl` + `jq` (preinstalled on recent macOS; else
`brew install jq`). Every script prints the response, looks up ids by name (vendor code, model, home name), and
stops with the HTTP status and Problem Details on any non-2xx.

| Script | Flow |
|---|---|
| `01-admin-register-vendor.sh AMAZON "Amazon"` | 1a: register a vendor |
| `02-admin-register-device.sh AMAZON AC AZ-AC12 "Amazon Smart AC 12K" powerState:SWITCH powerConsumption:POWER:KW_TO_W energyUsage:ENERGY temperature:TEMPERATURE` | 1b: add a model with mappings (`externalMetric:METRIC[:CONVERSION]`) |
| `03-user-register-home.sh "My home" Asia/Kolkata` | 2: register the home |
| `04-user-list-devices.sh [VENDOR] [TYPE]` | Show the catalogue the user picks from |
| `05-user-register-home-device.sh "My home" AZ-AC12 amz-ac-01 "Bedroom AC" 60` | 3: register the home device |
| `run-demo.sh` | All of the above for the Amazon AC, pausing between steps |

Run `./scripts/demo/run-demo.sh` on a **fresh database** (it registers Amazon and "My home", which conflict with
data the Postman collection already created). Point the scripts at another server with `BASE_URL=…`.

Checked: the scripts were run end to end against a stand-in server with the backend's response shapes — the happy
path, a duplicate registration (stops on `409`), an unknown model / home / vendor, a malformed mapping argument, a
default polling interval, and the backend not running ("Cannot reach … is the app running?").
