# Entity class diagram

JPA entities of `com.abhishek.smarthome.entity`. Relationships only map foreign keys (never navigated); related rows are loaded by id through repositories. `MetricMapping` is a value collection owned by `Device`.

```mermaid
classDiagram
    direction LR

    class Vendor {
        <<Entity>>
        UUID id
        VendorCode code  «unique»
        String name
        Instant createdAt
        long version
        +register(code, name, now)$ Vendor
    }

    class Home {
        <<Entity>>
        UUID id
        String name
        String timezone  «IANA, report day boundaries»
        Instant createdAt
        long version
        +register(name, timezone, now)$ Home
    }

    class Device {
        <<Entity · catalogue model>>
        UUID id
        UUID vendorId  «FK, read-only»
        DeviceType deviceType
        String model  «unique per vendor»
        String name
        List~MetricMapping~ metricMappings
        Instant createdAt
        long version
        +register(vendor, deviceType, model, name, mappings, now)$ Device
        +replaceMetricMappings(mappings) void
    }

    class MetricMapping {
        <<Embeddable · value object>>
        String externalMetric  «PK with device_id»
        MetricType metric
        String externalUnit
        String internalUnit  «= metric.unit»
        BigDecimal factor
        BigDecimal offset
        +of(externalMetric, metric, preset)$ MetricMapping
        +toCanonical(rawValue) double
        +verifySample(external, expected) void
    }

    class HomeDevice {
        <<Entity · physical appliance>>
        UUID id
        UUID homeId  «FK, read-only»
        UUID deviceId  «FK, read-only»
        String externalDeviceId  «unique per device»
        String name
        int pollingIntervalSeconds  «60–86400»
        boolean enabled
        Instant nextRunAt
        Instant lastRunAt  «nullable»
        Instant createdAt
        Instant updatedAt
        long version
        +register(home, device, externalDeviceId, name, interval, now)$ HomeDevice
        +isDue(now) boolean
        +markRun(now) void
        +scheduleRetry(now, delay) void
        +changePollingInterval(seconds, now) void
    }

    class DeviceReading {
        <<Entity · immutable>>
        UUID id
        UUID homeDeviceId  «FK»
        MetricType metric
        Instant time
        String value
        String unit
        Instant collectedAt
        +record(homeDeviceId, metric, time, value, unit, now)$ DeviceReading
    }

    class VendorCode {
        <<enumeration>>
        SAMSUNG
        AMAZON
        CISCO
    }

    class DeviceType {
        <<enumeration>>
        TV
        REFRIGERATOR
        AC
        OVEN
        WASHER
        DRYER
    }

    class MetricType {
        <<enumeration>>
        TEMPERATURE  C
        POWER  W
        ENERGY  kWh
        HUMIDITY  %
        RUNTIME  min
        DOOR_OPEN_COUNT
        SWITCH  on/off
    }

    Vendor "1" <-- "0..*" Device : vendor_id
    Device "1" *-- "1..*" MetricMapping : device_metric_mapping
    Home "1" <-- "0..*" HomeDevice : home_id
    Device "1" <-- "0..*" HomeDevice : device_id
    HomeDevice "1" <-- "0..*" DeviceReading : home_device_id

    Vendor ..> VendorCode
    Device ..> DeviceType
    MetricMapping ..> MetricType
    DeviceReading ..> MetricType
```
