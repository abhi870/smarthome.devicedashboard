package com.abhishek.smarthome.enums;

import java.util.Locale;

/**
 * External appliance vendors supported by the platform. The lower-case {@link #getCode()} is used in
 * configuration keys ({@code smarthome.vendors.<code>}) and in mock vendor URLs ({@code /api/v1/<code>}).
 */
public enum VendorCode {
	SAMSUNG,
	AMAZON,
	CISCO;

	public String getCode() {
		return name().toLowerCase(Locale.ROOT);
	}
}
