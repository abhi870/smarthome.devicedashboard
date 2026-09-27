package com.abhishek.smarthome.device.domain.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import java.util.UUID;

public class DeviceNotFoundException extends NotFoundException {

	public DeviceNotFoundException(UUID id) {
		super("Supported device " + id + " not found");
	}
}
