package com.abhishek.smarthome.vendor.adapter;

import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.List;

/**
 * Reads one vendor's metrics API. Each implementation knows its vendor's request parameters, paging style and
 * payload shape, and returns flattened {@link RawMetricSample}s. Adding a vendor = one new implementation bean.
 */
public interface VendorAdapter {

	VendorCode getVendorCode();

	/**
	 * Per-minute samples of one device with {@code from <= time < to}, oldest first, following all pages.
	 *
	 * @param externalDeviceId the device's id at the vendor
	 * @throws org.springframework.web.client.RestClientException if the vendor call fails
	 */
	List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to);
}
