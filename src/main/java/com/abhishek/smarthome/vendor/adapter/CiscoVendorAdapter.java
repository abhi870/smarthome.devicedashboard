package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.longValue;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.map;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.objects;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.getJson;
import static com.abhishek.smarthome.vendor.adapter.VendorRequests.query;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.client.VendorClientConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cisco: {@code GET /devices/{id}/metrics?since&limit} (epoch seconds, minutes). Cisco has no end time, so the
 * adapter asks for the minutes up to {@code to} (the collector never asks for more than 24 h) and drops samples at or
 * after it. Samples are already flat.
 */
@Component
@RequiredArgsConstructor
class CiscoVendorAdapter implements VendorAdapter {

	static final int MAX_LIMIT = 1440;

	@Qualifier(VendorClientConfig.CISCO)
	private final RestClient ciscoRestClient;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.CISCO;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		if (!from.isBefore(to)) {
			return List.of();
		}
		Map<String, Object> body = getJson(ciscoRestClient, "/devices/{id}/metrics",
				query("since", from.getEpochSecond(), "limit", minutesUntil(from, to)), externalDeviceId);
		return objects(map(body), "data").stream().map(CiscoVendorAdapter::toSample)
				.filter(sample -> sample.getTime().isBefore(to)).toList();
	}

	/** Whole minutes (rounded up) from {@code since} to {@code to}, between 1 and {@link #MAX_LIMIT}. */
	static int minutesUntil(Instant since, Instant to) {
		long minutes = Duration.between(since, to).toSeconds() / 60 + 1;
		return (int) Math.max(1, Math.min(MAX_LIMIT, minutes));
	}

	private static RawMetricSample toSample(Map<String, Object> item) {
		Map<String, Object> metrics = new LinkedHashMap<>(item);
		metrics.remove("ts");
		metrics.values().removeIf(Objects::isNull);
		return new RawMetricSample(Instant.ofEpochSecond(longValue(item.get("ts"), "ts")), metrics);
	}
}
