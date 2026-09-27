package com.abhishek.smarthome.home.domain.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import com.abhishek.smarthome.home.domain.entity.Home;
import java.util.UUID;

public class HomeNotFoundException extends NotFoundException {

	public HomeNotFoundException(UUID id) {
		super("Home " + id + " not found");
	}
}
