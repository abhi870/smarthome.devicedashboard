package com.abhishek.smarthome.vendor.domain.exception;

import com.abhishek.smarthome.common.error.NotFoundException;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.util.UUID;

public class VendorNotFoundException extends NotFoundException {

	public VendorNotFoundException(UUID id) {
		super("Vendor " + id + " not found");
	}
}
