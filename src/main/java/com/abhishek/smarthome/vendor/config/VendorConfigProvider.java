package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.vendor.Vendor;
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

	private final Map<Vendor, VendorProperties> vendors;

	public VendorConfigProvider(SmartHomeProperties properties) {
		Map<Vendor, VendorProperties> copy = new EnumMap<>(Vendor.class);
		copy.putAll(properties.getVendors());
		this.vendors = Collections.unmodifiableMap(copy);
	}

	/**
	 * @throws VendorNotConfiguredException if the vendor has no configuration
	 */
	public VendorProperties get(Vendor vendor) {
		VendorProperties config = vendors.get(vendor);
		if (config == null) {
			throw new VendorNotConfiguredException(vendor);
		}
		return config;
	}

	public Set<Vendor> configuredVendors() {
		return vendors.keySet();
	}

	public boolean isConfigured(Vendor vendor) {
		return vendors.containsKey(vendor);
	}
}
