package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.vendor.VendorCode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Read-only access to validated vendor configuration. The single entry point for other vendor
 * components, so they never bind or re-validate configuration themselves.
 */
@Component
public class VendorConfigProvider {

	private final Map<VendorCode, VendorProperties> vendors;

	public VendorConfigProvider(SmartHomeProperties properties) {
		Map<VendorCode, VendorProperties> copy = new EnumMap<>(VendorCode.class);
		copy.putAll(properties.getVendors());
		this.vendors = Collections.unmodifiableMap(copy);
	}

	/**
	 * @throws VendorNotConfiguredException if the vendor has no configuration
	 */
	public VendorProperties get(VendorCode vendor) {
		VendorProperties config = vendors.get(vendor);
		if (config == null) {
			throw new VendorNotConfiguredException(vendor);
		}
		return config;
	}

	public Set<VendorCode> configuredVendors() {
		return vendors.keySet();
	}

	public boolean isConfigured(VendorCode vendor) {
		return vendors.containsKey(vendor);
	}
}
