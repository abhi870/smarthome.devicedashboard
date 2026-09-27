package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.list;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.longValue;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.map;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.client.PageFetcher;
import com.abhishek.smarthome.vendor.client.VendorClientFactory;
import com.abhishek.smarthome.vendor.client.VendorPage;
import com.abhishek.smarthome.vendor.client.VendorPaginator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cisco: {@code GET /devices/{id}/metrics?since&limit} (epoch seconds, minutes), cursor paging via
 * {@code next_since}. Cisco has no end time, so the adapter asks only for the minutes left before {@code to}, stops
 * once the cursor reaches {@code to}, and drops samples at or after it. Samples are already flat:
 * {@code {ts, pwr_w, kwh_total, temp_c, state}}.
 */
@Component
class CiscoVendorAdapter implements VendorAdapter {

	static final int MAX_LIMIT = 1440;

	private final VendorClientFactory clients;

	CiscoVendorAdapter(VendorClientFactory clients) {
		this.clients = clients;
	}

	@Override
	public VendorCode vendorCode() {
		return VendorCode.CISCO;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		if (!from.isBefore(to)) {
			return List.of();
		}
		RestClient client = clients.clientFor(VendorCode.CISCO);
		PageFetcher<RawMetricSample> pages = cursor -> {
			long since = cursor == null ? from.getEpochSecond() : Long.parseLong(cursor);
			long minutesLeft = Math.max(1, Duration.between(Instant.ofEpochSecond(since), to).toSeconds() / 60 + 1);
			int limit = (int) Math.min(MAX_LIMIT, minutesLeft);
			Map<String, Object> body = client.get()
					.uri(uri -> uri.path("/devices/{id}/metrics").queryParam("since", since).queryParam("limit", limit)
							.build(externalDeviceId))
					.retrieve().body(JsonMaps.MAP);
			List<RawMetricSample> samples = new ArrayList<>();
			for (Object item : list(map(body).get("data"))) {
				RawMetricSample sample = toSample(map(item));
				if (sample.getTime().isBefore(to)) {
					samples.add(sample);
				}
			}
			long nextSince = longValue(map(body).get("next_since"), "next_since");
			return nextSince < to.getEpochSecond() && nextSince > since
					? VendorPage.of(samples, String.valueOf(nextSince)) : VendorPage.last(samples);
		};
		return VendorPaginator.fetchAll(pages);
	}

	private static RawMetricSample toSample(Map<String, Object> item) {
		Map<String, Object> metrics = new LinkedHashMap<>(item);
		metrics.remove("ts");
		metrics.values().removeIf(value -> value == null);
		return new RawMetricSample(Instant.ofEpochSecond(longValue(item.get("ts"), "ts")), metrics);
	}
}
