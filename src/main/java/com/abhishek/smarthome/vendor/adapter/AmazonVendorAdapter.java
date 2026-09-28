package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.longValue;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Amazon: {@code GET /devices/{id}/metrics?startTime&endTime} (epoch ms). Datapoints are {@code {t, properties:[{name, value, unit}]}}; flattened to
 * {@code name} → value, e.g. {@code powerConsumption} (kW), {@code powerState} (ON/OFF).
 */
@Component
@RequiredArgsConstructor
class AmazonVendorAdapter implements VendorAdapter {

	@Qualifier(VendorClientConfig.AMAZON)
	private final RestClient amazonRestClient;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.AMAZON;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		Map<String, Object> body = getJson(amazonRestClient, "/devices/{id}/metrics",
				query("startTime", from.toEpochMilli(), "endTime", to.toEpochMilli()), externalDeviceId);
		return objects(map(body), "datapoints").stream().map(AmazonVendorAdapter::toSample).toList();
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
