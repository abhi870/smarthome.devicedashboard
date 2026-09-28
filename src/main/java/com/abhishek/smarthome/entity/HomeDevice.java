package com.abhishek.smarthome.entity;

import com.abhishek.smarthome.exception.InvalidPollingIntervalException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A physical appliance a user registered in a {@link Home}, of a supported catalogue {@link Device} model.
 * Owns both relationships (many home devices → one home, many home devices → one catalogue device); the vendor
 * is the device's vendor. Identified at the vendor by {@code externalDeviceId}. The relationships only map the
 * foreign keys and are never navigated (no getters); read {@link #homeId} / {@link #deviceId} and load the related
 * rows through their repositories.
 *
 * <p>Scheduling: metrics are collected every {@code pollingIntervalSeconds}. {@code nextRunAt} is when the next
 * collection is due — the registration time at first (so it runs immediately), then {@code lastRunAt + interval}
 * after each run ({@link #markRun}). Changing the interval recomputes it ({@link #changePollingInterval}).
 */
@Entity
@Table(name = "home_device", uniqueConstraints = @UniqueConstraint(name = "uk_home_device_device_external_id",
		columnNames = { "device_id", "external_device_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class HomeDevice {

	@Id
	private UUID id;

	/** FK only (written on insert); never navigated. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "home_id", nullable = false)
	@Getter(AccessLevel.NONE)
	private Home home;

	/** Read-only view of the {@code home_id} column. */
	@Column(name = "home_id", nullable = false, insertable = false, updatable = false)
	private UUID homeId;

	/** FK only (written on insert); never navigated. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	@Getter(AccessLevel.NONE)
	private Device device;

	/** Read-only view of the {@code device_id} column. */
	@Column(name = "device_id", nullable = false, insertable = false, updatable = false)
	private UUID deviceId;

	/** The appliance's id at its vendor (used when fetching metrics). */
	@Column(name = "external_device_id", nullable = false, length = 100)
	private String externalDeviceId;

	/** User-chosen name, e.g. "Bedroom AC". */
	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "polling_interval_seconds", nullable = false)
	private int pollingIntervalSeconds;

	@Column(nullable = false)
	private boolean enabled;

	/** When the next metrics collection is due. */
	@Column(name = "next_run_at", nullable = false)
	private Instant nextRunAt;

	/** When metrics were last collected; {@code null} until the first run. */
	@Column(name = "last_run_at")
	private @Nullable Instant lastRunAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	public static final int MIN_POLLING_INTERVAL_SECONDS = 60;
	public static final int MAX_POLLING_INTERVAL_SECONDS = 86_400;

	/**
	 * @throws InvalidPollingIntervalException if the interval is outside 60 s – 24 h
	 */
	public static HomeDevice register(Home home, Device device, String externalDeviceId, String name,
			int pollingIntervalSeconds, Instant now) {
		requireValidInterval(pollingIntervalSeconds);
		HomeDevice homeDevice = new HomeDevice();
		homeDevice.id = UUID.randomUUID();
		homeDevice.home = home;
		homeDevice.homeId = home.getId();
		homeDevice.device = device;
		homeDevice.deviceId = device.getId();
		homeDevice.externalDeviceId = externalDeviceId;
		homeDevice.name = name;
		homeDevice.pollingIntervalSeconds = pollingIntervalSeconds;
		homeDevice.enabled = true;
		homeDevice.nextRunAt = now;
		homeDevice.createdAt = now;
		homeDevice.updatedAt = now;
		return homeDevice;
	}

	/** Whether a collection is due at {@code now}: enabled and {@code nextRunAt <= now}. */
	public boolean isDue(Instant now) {
		return enabled && !nextRunAt.isAfter(now);
	}

	/** Records a collection run at {@code now} and schedules the next one {@code pollingIntervalSeconds} later. */
	public void markRun(Instant now) {
		lastRunAt = now;
		nextRunAt = now.plusSeconds(pollingIntervalSeconds);
		updatedAt = now;
	}

	/**
	 * A collection attempt failed at {@code now}: try again after {@code delay} (capped at one interval) without
	 * moving {@code lastRunAt}, so the next run still covers the missed period.
	 */
	public void scheduleRetry(Instant now, Duration delay) {
		Duration interval = Duration.ofSeconds(pollingIntervalSeconds);
		nextRunAt = now.plus(delay.compareTo(interval) < 0 ? delay : interval);
		updatedAt = now;
	}

	/**
	 * Changes the interval and reschedules: {@code lastRunAt + newInterval}, or {@code now} if that is already past
	 * (or there was no run yet), so a shorter interval takes effect on the next scheduler tick.
	 *
	 * @throws InvalidPollingIntervalException if the interval is outside 60 s – 24 h
	 */
	public void changePollingInterval(int seconds, Instant now) {
		requireValidInterval(seconds);
		pollingIntervalSeconds = seconds;
		Instant next = lastRunAt == null ? now : lastRunAt.plusSeconds(seconds);
		nextRunAt = next.isBefore(now) ? now : next;
		updatedAt = now;
	}

	private static void requireValidInterval(int seconds) {
		if (seconds < MIN_POLLING_INTERVAL_SECONDS || seconds > MAX_POLLING_INTERVAL_SECONDS) {
			throw new InvalidPollingIntervalException(seconds, MIN_POLLING_INTERVAL_SECONDS,
					MAX_POLLING_INTERVAL_SECONDS);
		}
	}
}
