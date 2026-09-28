package com.abhishek.smarthome.dto.output.homedevice;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** A device registered in a home, with the catalogue model and vendor it belongs to. */
@Getter
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

	private HomeDeviceResponse(HomeDevice hd, Device device, Vendor vendor) {
		this.id = hd.getId();
		this.homeId = hd.getHomeId();
		this.deviceId = hd.getDeviceId();
		this.vendorCode = vendor.getCode();
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

	/** {@code device} and {@code vendor} are the home device's catalogue device and its vendor, loaded by the service. */
	public static HomeDeviceResponse from(HomeDevice homeDevice, Device device, Vendor vendor) {
		return new HomeDeviceResponse(homeDevice, device, vendor);
	}
}
