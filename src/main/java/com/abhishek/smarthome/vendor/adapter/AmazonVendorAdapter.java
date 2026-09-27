package com.abhishek.smarthome.vendor.adapter;

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
 * Amazon: one call per device, {@code GET /devices/{id}/metrics?startTime&endTime} (epoch ms), token paging
 * ({@code maxResults}, {@code nextToken}). Datapoints are {@code {t, properties:[{name, value, unit}]}}; flattened to
 * {@code name} → value, e.g. {@code powerConsumption} (kW), {@code powerState} (ON/OFF).
 */
@Component
@RequiredArgsConstructor
class AmazonVendorAdapter implements VendorAdapter {

	static final int MAX_RESULTS = 500;

	private final VendorClientFactory vendorClientFactory;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.AMAZON;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		return VendorPaginator.fetchAll(cursor -> fetchPage(externalDeviceId, from, to, cursor));
	}

	private VendorPage<RawMetricSample> fetchPage(String deviceId, Instant from, Instant to, @Nullable String token) {
		Map<String, Object> body = getJson(vendorClientFactory.clientFor(VendorCode.AMAZON), "/devices/{id}/metrics",
				query("startTime", from.toEpochMilli(), "endTime", to.toEpochMilli(), "maxResults", MAX_RESULTS,
						"nextToken", token),
				deviceId);
		List<RawMetricSample> samples = objects(map(body), "datapoints").stream().map(AmazonVendorAdapter::toSample)
				.toList();
		Object nextToken = map(body).get("nextToken");
		return nextToken == null ? VendorPage.last(samples) : VendorPage.of(samples, nextToken.toString());
	}

	private static RawMetricSample toSample(Map<String, Object> datapoint) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		for (Map<String, Object> property : objects(datapoint, "properties")) {
			if (property.get("name") != null && property.get("value") != null) {
				metrics.put(property.get("name").toString(), property.get("value"));
			}
		}
		return new RawMetricSample(Instant.ofEpochMilli(longValue(datapoint.get("t"), "t")), metrics);
	}
}
