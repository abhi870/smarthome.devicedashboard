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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Cisco: {@code GET /devices/{id}/metrics?since&limit} (epoch seconds, minutes), cursor paging via
 * {@code next_since}. Cisco has no end time, so the adapter asks only for the minutes left before {@code to}, stops
 * once the cursor reaches {@code to}, and drops samples at or after it. Samples are already flat.
 */
@Component
@RequiredArgsConstructor
class CiscoVendorAdapter implements VendorAdapter {

	static final int MAX_LIMIT = 1440;

	private final VendorClientFactory vendorClientFactory;

	@Override
	public VendorCode getVendorCode() {
		return VendorCode.CISCO;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		if (!from.isBefore(to)) {
			return List.of();
		}
		return VendorPaginator.fetchAll(cursor -> fetchPage(externalDeviceId, from, to, cursor));
	}

	private VendorPage<RawMetricSample> fetchPage(String deviceId, Instant from, Instant to, @Nullable String cursor) {
		long since = cursor == null ? from.getEpochSecond() : Long.parseLong(cursor);
		int limit = minutesUntil(Instant.ofEpochSecond(since), to);
		Map<String, Object> body = getJson(vendorClientFactory.clientFor(VendorCode.CISCO), "/devices/{id}/metrics",
				query("since", since, "limit", limit), deviceId);
		List<RawMetricSample> samples = objects(map(body), "data").stream().map(CiscoVendorAdapter::toSample)
				.filter(sample -> sample.getTime().isBefore(to)).toList();
		long nextSince = longValue(map(body).get("next_since"), "next_since");
		boolean hasNext = nextSince < to.getEpochSecond() && nextSince > since;
		return hasNext ? VendorPage.of(samples, String.valueOf(nextSince)) : VendorPage.last(samples);
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
