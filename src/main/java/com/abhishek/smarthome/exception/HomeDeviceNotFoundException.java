package com.abhishek.smarthome.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import com.abhishek.smarthome.entity.Home;
import java.util.UUID;

public class HomeDeviceNotFoundException extends NotFoundException {

	public HomeDeviceNotFoundException(UUID id) {
		super("Home device " + id + " not found");
	}
}
