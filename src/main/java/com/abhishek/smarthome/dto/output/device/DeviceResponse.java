package com.abhishek.smarthome.dto.output.device;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** A supported device model from the catalogue, with its metric mappings. */
@Getter
public final class DeviceResponse {

	private final UUID id;
	private final UUID vendorId;
	private final VendorCode vendorCode;
	private final DeviceType deviceType;
	private final String model;
	private final String name;
	private final List<MetricMappingResponse> mappings;
	private final Instant createdAt;

	private DeviceResponse(Device d) {
		this.id = d.getId();
		this.vendorId = d.getVendor().getId();
		this.vendorCode = d.getVendor().getCode();
		this.deviceType = d.getDeviceType();
		this.model = d.getModel();
		this.name = d.getName();
		this.mappings = d.getMetricMappings().stream().map(MetricMappingResponse::from).toList();
		this.createdAt = d.getCreatedAt();
	}

	public static DeviceResponse from(Device device) {
		return new DeviceResponse(device);
	}
}
