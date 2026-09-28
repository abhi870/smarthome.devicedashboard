package com.abhishek.smarthome.dto.input.homedevice;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Everything the collector needs about one due home device, copied out of the entity inside a transaction so the
 * vendor call can run without a persistence context: ids, vendor, schedule and the catalogue device's mappings.
 */
@Getter
public final class CollectionTarget {

	private final UUID homeDeviceId;
	private final VendorCode vendorCode;
	private final String externalDeviceId;
	private final int pollingIntervalSeconds;
	private final @Nullable Instant lastRunAt;
	private final List<MetricMapping> mappings;

	public CollectionTarget(UUID homeDeviceId, VendorCode vendorCode, String externalDeviceId,
			int pollingIntervalSeconds, @Nullable Instant lastRunAt, List<MetricMapping> mappings) {
		this.homeDeviceId = homeDeviceId;
		this.vendorCode = vendorCode;
		this.externalDeviceId = externalDeviceId;
		this.pollingIntervalSeconds = pollingIntervalSeconds;
		this.lastRunAt = lastRunAt;
		this.mappings = List.copyOf(mappings);
	}

	/** {@code device} and {@code vendor} are the home device's catalogue device and its vendor, loaded by the service. */
	public static CollectionTarget of(HomeDevice homeDevice, Device device, Vendor vendor) {
		return new CollectionTarget(homeDevice.getId(), vendor.getCode(), homeDevice.getExternalDeviceId(),
				homeDevice.getPollingIntervalSeconds(), homeDevice.getLastRunAt(), device.getMetricMappings());
	}

	@Override
	public String toString() {
		return "CollectionTarget[homeDevice=" + homeDeviceId + ", vendor=" + vendorCode + ", externalId="
				+ externalDeviceId + ", lastRunAt=" + lastRunAt + "]";
	}
}
