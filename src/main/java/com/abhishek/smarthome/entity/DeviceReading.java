package com.abhishek.smarthome.entity;

import com.abhishek.smarthome.enums.MetricType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * The value of one metric of a home device at a point in time, e.g. Bedroom AC / POWER / 10:05 / {@code "1150.5"}
 * {@code W}, or Bedroom AC / SWITCH / 10:05 / {@code "ON"}. Value and unit are plain string columns so reports read
 * them directly (numeric metrics: {@code CAST(reading_value AS DOUBLE PRECISION)}); rows are keyed and indexed by {@code (homeDeviceId, metric, time DESC)} so per-device, per-metric reports read one index range.
 * Append-only (no {@code @Version}, no setters) and references the home device by id only (no JPA relationship).
 *
 * <p>{@link Persistable}: the id is assigned in {@link #record}, so without this Spring Data's {@code save} would
 * treat every new reading as existing and {@code merge} it (one SELECT per row before the INSERT, and no batching).
 */
@Entity
@Table(name = "device_reading", indexes = {
        @Index(name = "uk_device_reading_device_metric_time", columnList = "home_device_id, metric, reading_time DESC",
                unique = true),
        @Index(name = "ix_device_reading_device_time", columnList = "home_device_id, reading_time DESC")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class DeviceReading implements Persistable<UUID> {

    public static final int MAX_VALUE_LENGTH = 100;
    public static final int MAX_UNIT_LENGTH = 20;

    @Id
    private UUID id;

    @Column(name = "home_device_id", nullable = false, updatable = false)
    private UUID homeDeviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private MetricType metric;

    /**
     * When the value was measured (vendor time).
     */
    @Column(name = "reading_time", nullable = false, updatable = false)
    private Instant time;

    /**
     * The value as text: a number for numeric metrics ({@code "1150.5"}), a state for SWITCH ({@code "ON"}).
     */
    @Column(name = "reading_value", nullable = false, updatable = false, length = MAX_VALUE_LENGTH)
    private String value;

    /**
     * Unit of {@link #value}, e.g. {@code W}, {@code C}, {@code on/off}.
     */
    @Column(nullable = false, updatable = false, length = MAX_UNIT_LENGTH)
    private String unit;

    /**
     * When this backend stored it.
     */
    @Column(name = "collected_at", nullable = false, updatable = false)
    private Instant collectedAt;

    /** {@code true} until persisted or when loaded; drives {@link #isNew()}. */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        newEntity = false;
    }

    public static DeviceReading record(UUID homeDeviceId, MetricType metric, Instant time, String value, String unit,
                                       Instant collectedAt) {
        DeviceReading reading = new DeviceReading();
        reading.id = UUID.randomUUID();
        reading.homeDeviceId = homeDeviceId;
        reading.metric = metric;
        reading.time = time;
        reading.value = value;
        reading.unit = unit;
        reading.collectedAt = collectedAt;
        return reading;
    }
}
