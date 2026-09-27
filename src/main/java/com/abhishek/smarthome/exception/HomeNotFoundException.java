package com.abhishek.smarthome.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import com.abhishek.smarthome.entity.Home;
import java.util.UUID;

public class HomeNotFoundException extends NotFoundException {

	public HomeNotFoundException(UUID id) {
		super("Home " + id + " not found");
	}
}
