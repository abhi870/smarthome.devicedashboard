package com.abhishek.smarthome.dto.input.device;

import com.abhishek.smarthome.enums.DeviceType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Body of {@code POST /api/v1/smart-home/devices/register} (admin: add a supported device model). */
public final class RegisterDeviceRequest {

	@NotNull
	private final UUID vendorId;

	@NotNull
	private final DeviceType deviceType;

	@NotBlank
	private final String model;

	@NotBlank
	private final String name;

	/** How this model's vendor metrics map to canonical metrics; at least one. */
	@NotEmpty
	private final List<@Valid @NotNull MetricMappingRequest> mappings;

	@JsonCreator
	public RegisterDeviceRequest(@JsonProperty("vendorId") UUID vendorId,
			@JsonProperty("deviceType") DeviceType deviceType, @JsonProperty("model") String model,
			@JsonProperty("name") String name, @JsonProperty("mappings") List<MetricMappingRequest> mappings) {
		this.vendorId = vendorId;
		this.deviceType = deviceType;
		this.model = model;
		this.name = name;
		this.mappings = mappings;
	}

	public RegisterDeviceCommand toCommand() {
		return new RegisterDeviceCommand(vendorId, deviceType, model, name,
				mappings.stream().map(MetricMappingRequest::toMapping).toList());
	}

	public UUID getVendorId() {
		return vendorId;
	}

	public DeviceType getDeviceType() {
		return deviceType;
	}

	public String getModel() {
		return model;
	}

	public String getName() {
		return name;
	}

	public List<MetricMappingRequest> getMappings() {
		return mappings;
	}
}
