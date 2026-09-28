package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.at;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.map;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.objects;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.getJson;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.query;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.client.VendorClientConfig;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Samsung: {@code GET /devices/{id}/metrics?from&to} (ISO). Readings nest as
 * {@code components.main.<capability>.<attribute>.value}; flattened to {@code <capability>.<attribute>}, e.g. {@code powerConsumptionReport.power}, {@code switch.switch}.
 */
@Component
@RequiredArgsConstructor
class SamsungVendorAdapter implements VendorAdapter {

	@Qualifier(VendorClientConfig.SAMSUNG)
	private final RestClient samsungRestClient;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.SAMSUNG;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		Map<String, Object> body = getJson(samsungRestClient, "/devices/{id}/metrics",
				query("from", from, "to", to), externalDeviceId);
		return objects(map(body), "samples").stream().map(SamsungVendorAdapter::toSample).toList();
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
