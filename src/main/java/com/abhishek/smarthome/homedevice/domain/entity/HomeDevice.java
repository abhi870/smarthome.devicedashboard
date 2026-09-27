package com.abhishek.smarthome.homedevice.domain.entity;

import com.abhishek.smarthome.device.domain.entity.Device;
import com.abhishek.smarthome.home.domain.entity.Home;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * A physical appliance a user registered in a {@link Home}, of a supported catalogue {@link Device} model.
 * Owns both relationships (many home devices → one home, many home devices → one catalogue device); the vendor
 * is {@code device.vendor}. Identified at the vendor by {@code externalDeviceId}; metrics are collected every
 * {@code pollingIntervalSeconds}, the first collection is due immediately.
 */
@Entity
@Table(name = "home_device", uniqueConstraints = @UniqueConstraint(name = "uk_home_device_device_external_id",
		columnNames = { "device_id", "external_device_id" }))
@Getter
public class HomeDevice {

	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "home_id", nullable = false)
	private Home home;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

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

	@Column(name = "next_poll_at", nullable = false)
	private Instant nextPollAt;

	@Column(name = "last_polled_at")
	private @Nullable Instant lastPolledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected HomeDevice() {
		// for JPA
	}

	public static HomeDevice register(Home home, Device device, String externalDeviceId, String name,
			int pollingIntervalSeconds, Instant now) {
		HomeDevice homeDevice = new HomeDevice();
		homeDevice.id = UUID.randomUUID();
		homeDevice.home = home;
		homeDevice.device = device;
		homeDevice.externalDeviceId = externalDeviceId;
		homeDevice.name = name;
		homeDevice.pollingIntervalSeconds = pollingIntervalSeconds;
		homeDevice.enabled = true;
		homeDevice.nextPollAt = now;
		homeDevice.createdAt = now;
		homeDevice.updatedAt = now;
		return homeDevice;
	}
}
