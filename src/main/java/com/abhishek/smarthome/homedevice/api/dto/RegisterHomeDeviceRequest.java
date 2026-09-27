package com.abhishek.smarthome.homedevice.api.dto;

import com.abhishek.smarthome.homedevice.domain.service.RegisterHomeDeviceCommand;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /api/v1/smart-home/home-devices/register}. */
public final class RegisterHomeDeviceRequest {

	public static final int DEFAULT_POLLING_INTERVAL_SECONDS = 300;

	@NotNull
	private final UUID homeId;

	/** A supported device from {@code GET /api/v1/smart-home/devices}. */
	@NotNull
	private final UUID deviceId;

	@NotBlank
	private final String externalDeviceId;

	@NotBlank
	private final String name;

	/** Optional; defaults to {@value #DEFAULT_POLLING_INTERVAL_SECONDS}. */
	private final @Nullable Integer pollingIntervalSeconds;

	@JsonCreator
	public RegisterHomeDeviceRequest(@JsonProperty("homeId") UUID homeId, @JsonProperty("deviceId") UUID deviceId,
			@JsonProperty("externalDeviceId") String externalDeviceId, @JsonProperty("name") String name,
			@JsonProperty("pollingIntervalSeconds") @Nullable Integer pollingIntervalSeconds) {
		this.homeId = homeId;
		this.deviceId = deviceId;
		this.externalDeviceId = externalDeviceId;
		this.name = name;
		this.pollingIntervalSeconds = pollingIntervalSeconds;
	}

	public RegisterHomeDeviceCommand toCommand() {
		return new RegisterHomeDeviceCommand(homeId, deviceId, externalDeviceId, name,
				pollingIntervalSeconds == null ? DEFAULT_POLLING_INTERVAL_SECONDS : pollingIntervalSeconds);
	}

	public UUID getHomeId() {
		return homeId;
	}

	public UUID getDeviceId() {
		return deviceId;
	}

	public String getExternalDeviceId() {
		return externalDeviceId;
	}

	public String getName() {
		return name;
	}

	public @Nullable Integer getPollingIntervalSeconds() {
		return pollingIntervalSeconds;
	}
}
