package com.abhishek.smarthome.homedevice.domain.service;

import java.util.UUID;

/** Input for registering a device in a home, independent of the HTTP layer. */
public final class RegisterHomeDeviceCommand {

	private final UUID homeId;
	private final UUID deviceId;
	private final String externalDeviceId;
	private final String name;
	private final int pollingIntervalSeconds;

	public RegisterHomeDeviceCommand(UUID homeId, UUID deviceId, String externalDeviceId, String name,
			int pollingIntervalSeconds) {
		this.homeId = homeId;
		this.deviceId = deviceId;
		this.externalDeviceId = externalDeviceId;
		this.name = name;
		this.pollingIntervalSeconds = pollingIntervalSeconds;
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

	public int getPollingIntervalSeconds() {
		return pollingIntervalSeconds;
	}
}
