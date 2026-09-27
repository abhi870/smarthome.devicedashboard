package com.abhishek.smarthome.vendor.adapter;

import com.abhishek.smarthome.enums.VendorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Looks up the {@link VendorAdapter} for a vendor code. Fails fast if two adapters claim the same vendor. */
@Component
public class VendorAdapterRegistry {

	private final Map<VendorCode, VendorAdapter> adapters = new EnumMap<>(VendorCode.class);

	public VendorAdapterRegistry(List<VendorAdapter> adapters) {
		for (VendorAdapter adapter : adapters) {
			VendorAdapter previous = this.adapters.put(adapter.vendorCode(), adapter);
			if (previous != null) {
				throw new IllegalStateException("Duplicate vendor adapters for %s: %s and %s".formatted(
						adapter.vendorCode(), previous.getClass().getSimpleName(), adapter.getClass().getSimpleName()));
			}
		}
	}

	/**
	 * @throws IllegalStateException if no adapter exists for the vendor
	 */
	public VendorAdapter adapterFor(VendorCode vendor) {
		VendorAdapter adapter = adapters.get(vendor);
		if (adapter == null) {
			throw new IllegalStateException("No vendor adapter for " + vendor + "; available: " + adapters.keySet());
		}
		return adapter;
	}
}
