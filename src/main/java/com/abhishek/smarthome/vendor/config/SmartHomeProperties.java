package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.vendor.VendorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Root {@code smarthome.*} configuration. VendorCode keys must match a {@link VendorCode} constant
 * (e.g. {@code smarthome.vendors.samsung}); unknown keys fail startup.
 */
@Validated
@ConfigurationProperties("smarthome")
public final class SmartHomeProperties {

	@NotEmpty
	private final Map<VendorCode, @Valid VendorProperties> vendors;

	public SmartHomeProperties(Map<VendorCode, VendorProperties> vendors) {
		this.vendors = vendors;
	}

	public Map<VendorCode, VendorProperties> getVendors() {
		return vendors;
	}
}
