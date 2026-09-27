package com.abhishek.smarthome.vendor.adapter;

import com.abhishek.smarthome.enums.VendorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Looks up the {@link VendorAdapter} for a vendor code. Fails fast if two adapters claim the same vendor. */
@Component
public class VendorAdapterRegistry {

	private final Map<VendorCode, VendorAdapter> adaptersByVendor = new EnumMap<>(VendorCode.class);

	public VendorAdapterRegistry(List<VendorAdapter> adaptersByVendor) {
		for (VendorAdapter adapter : adaptersByVendor) {
			VendorAdapter previous = this.adaptersByVendor.put(adapter.getVendorCode(), adapter);
			if (previous != null) {
				throw new IllegalStateException("Duplicate vendor adapters for %s: %s and %s".formatted(
						adapter.getVendorCode(), previous.getClass().getSimpleName(), adapter.getClass().getSimpleName()));
			}
		}
	}

	/**
	 * @throws IllegalStateException if no adapter exists for the vendor
	 */
	public VendorAdapter adapterFor(VendorCode vendor) {
		VendorAdapter adapter = adaptersByVendor.get(vendor);
		if (adapter == null) {
			throw new IllegalStateException("No vendor adapter for " + vendor + "; available: " + adaptersByVendor.keySet());
		}
		return adapter;
	}
}
