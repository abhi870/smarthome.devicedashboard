package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.at;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.longValue;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.map;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.objects;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.getJson;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.query;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.client.VendorClientFactory;
import com.abhishek.smarthome.vendor.client.VendorPage;
import com.abhishek.smarthome.vendor.client.VendorPaginator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Samsung: {@code GET /devices/{id}/metrics?from&to} (ISO), page-number paging ({@code page}, {@code size},
 * {@code page.totalPages}). Readings nest as {@code components.main.<capability>.<attribute>.value}; flattened to
 * {@code <capability>.<attribute>}, e.g. {@code powerConsumptionReport.power}, {@code switch.switch}.
 */
@Component
@RequiredArgsConstructor
class SamsungVendorAdapter implements VendorAdapter {

	static final int PAGE_SIZE = 500;

	private final VendorClientFactory vendorClientFactory;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.SAMSUNG;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		return VendorPaginator.fetchAll(cursor -> fetchPage(externalDeviceId, from, to, cursor));
	}

	private VendorPage<RawMetricSample> fetchPage(String deviceId, Instant from, Instant to, @Nullable String cursor) {
		int page = cursor == null ? 0 : Integer.parseInt(cursor);
		Map<String, Object> body = getJson(vendorClientFactory.clientFor(VendorCode.SAMSUNG), "/devices/{id}/metrics",
				query("from", from, "to", to, "page", page, "size", PAGE_SIZE), deviceId);
		List<RawMetricSample> samples = objects(map(body), "samples").stream().map(SamsungVendorAdapter::toSample)
				.toList();
		boolean hasNext = page + 1 < longValue(at(body, "page").get("totalPages"), "page.totalPages");
		return hasNext ? VendorPage.of(samples, String.valueOf(page + 1)) : VendorPage.last(samples);
	}

	private static RawMetricSample toSample(Map<String, Object> sample) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		at(sample, "components", "main").forEach((capability, attributes) -> map(attributes)
				.forEach((attribute, reading) -> putIfPresent(metrics, capability + "." + attribute,
						map(reading).get("value"))));
		return new RawMetricSample(Instant.parse(String.valueOf(sample.get("timestamp"))), metrics);
	}

	private static void putIfPresent(Map<String, Object> metrics, String name, @Nullable Object value) {
		if (value != null) {
			metrics.put(name, value);
		}
	}
}
