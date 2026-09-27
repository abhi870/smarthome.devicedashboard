package com.abhishek.smarthome.dto.output.homedevice;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A device registered in a home, with the catalogue model and vendor it belongs to. */
public final class HomeDeviceResponse {

	private final UUID id;
	private final UUID homeId;
	private final UUID deviceId;
	private final VendorCode vendorCode;
	private final DeviceType deviceType;
	private final String model;
	private final String externalDeviceId;
	private final String name;
	private final int pollingIntervalSeconds;
	private final boolean enabled;
	private final Instant nextRunAt;
	private final @Nullable Instant lastRunAt;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final long version;

	private HomeDeviceResponse(HomeDevice hd) {
		Device device = hd.getDevice();
		this.id = hd.getId();
		this.homeId = hd.getHome().getId();
		this.deviceId = device.getId();
		this.vendorCode = device.getVendor().getCode();
		this.deviceType = device.getDeviceType();
		this.model = device.getModel();
		this.externalDeviceId = hd.getExternalDeviceId();
		this.name = hd.getName();
		this.pollingIntervalSeconds = hd.getPollingIntervalSeconds();
		this.enabled = hd.isEnabled();
		this.nextRunAt = hd.getNextRunAt();
		this.lastRunAt = hd.getLastRunAt();
		this.createdAt = hd.getCreatedAt();
		this.updatedAt = hd.getUpdatedAt();
		this.version = hd.getVersion();
	}

	public static HomeDeviceResponse from(HomeDevice homeDevice) {
		return new HomeDeviceResponse(homeDevice);
	}

	public UUID getId() {
		return id;
	}

	public UUID getHomeId() {
		return homeId;
	}

	public UUID getDeviceId() {
		return deviceId;
	}

	public VendorCode getVendorCode() {
		return vendorCode;
	}

	public DeviceType getDeviceType() {
		return deviceType;
	}

	public String getModel() {
		return model;
	}

	public String getExternalDeviceId() {
		return externalDeviceId;
	}

	public String getName() {
		return name;
	}

	public int getPollingIntervalSeconds() {
		return pollingIntervalSeconds;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getNextRunAt() {
		return nextRunAt;
	}

	public @Nullable Instant getLastRunAt() {
		return lastRunAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}
}
