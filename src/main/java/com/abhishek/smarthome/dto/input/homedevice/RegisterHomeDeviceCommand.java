package com.abhishek.smarthome.dto.input.homedevice;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Input for registering a device in a home, independent of the HTTP layer. */
@Getter
@RequiredArgsConstructor
public final class RegisterHomeDeviceCommand {

	private final UUID homeId;
	private final UUID deviceId;
	private final String externalDeviceId;
	private final String name;
	private final int pollingIntervalSeconds;
}
