package com.abhishek.smarthome.vendor;

import java.util.Locale;

/**
 * External appliance vendors supported by the platform. The lower-case {@link #code()} is used in
 * configuration keys ({@code smarthome.vendors.<code>}) and in mock vendor URLs ({@code /api/v1/<code>}).
 */
public enum Vendor {
	SAMSUNG,
	AMAZON,
	CISCO;

	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}
}
