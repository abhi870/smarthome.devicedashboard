package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.vendor.Vendor;

/** Thrown when code asks for a vendor that has no {@code smarthome.vendors.<vendor>} configuration. */
public class VendorNotConfiguredException extends RuntimeException {

	public VendorNotConfiguredException(Vendor vendor) {
		super("Vendor '%s' is not configured; add smarthome.vendors.%s to application.yaml"
				.formatted(vendor, vendor.code()));
	}
}
