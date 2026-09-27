package com.abhishek.smarthome.dto.input.device;

import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.DeviceType;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Input for adding a supported device model to the catalogue, independent of the HTTP layer. */
@Getter
public final class RegisterDeviceCommand {

	private final UUID vendorId;
	private final DeviceType deviceType;
	private final String model;
	private final String name;
	private final List<MetricMapping> metricMappings;

	public RegisterDeviceCommand(UUID vendorId, DeviceType deviceType, String model, String name,
			List<MetricMapping> metricMappings) {
		this.vendorId = vendorId;
		this.deviceType = deviceType;
		this.model = model;
		this.name = name;
		this.metricMappings = List.copyOf(metricMappings);
	}
}
