package com.abhishek.smarthome.vendor.adapter;

import static com.abhishek.smarthome.vendor.adapter.JsonMaps.list;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.longValue;
import static com.abhishek.smarthome.vendor.adapter.JsonMaps.map;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.client.PageFetcher;
import com.abhishek.smarthome.vendor.client.VendorClientFactory;
import com.abhishek.smarthome.vendor.client.VendorPage;
import com.abhishek.smarthome.vendor.client.VendorPaginator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Amazon: batch {@code GET /devices/metrics?ids&startTime&endTime} (epoch ms), token paging ({@code maxResults},
 * {@code nextToken}). Datapoints are {@code {t, properties:[{name, value, unit}]}}; flattened to {@code name} → value,
 * e.g. {@code powerConsumption} (kW), {@code powerState} (ON/OFF).
 */
@Component
class AmazonVendorAdapter implements VendorAdapter {

	static final int MAX_RESULTS = 500;

	private final VendorClientFactory clients;

	AmazonVendorAdapter(VendorClientFactory clients) {
		this.clients = clients;
	}

	@Override
	public VendorCode vendorCode() {
		return VendorCode.AMAZON;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		RestClient client = clients.clientFor(VendorCode.AMAZON);
		PageFetcher<RawMetricSample> pages = cursor -> {
			Map<String, Object> body = client.get()
					.uri(uri -> {
						uri.path("/devices/metrics").queryParam("ids", externalDeviceId)
								.queryParam("startTime", from.toEpochMilli()).queryParam("endTime", to.toEpochMilli())
								.queryParam("maxResults", MAX_RESULTS);
						if (cursor != null) {
							uri.queryParam("nextToken", cursor);
						}
						return uri.build();
					})
					.retrieve().body(JsonMaps.MAP);
			List<RawMetricSample> samples = new ArrayList<>();
			for (Object result : list(map(body).get("results"))) {
				if (externalDeviceId.equals(map(result).get("applianceId"))) {
					for (Object datapoint : list(map(result).get("datapoints"))) {
						samples.add(toSample(map(datapoint)));
					}
				}
			}
			Object nextToken = map(body).get("nextToken");
			return nextToken == null ? VendorPage.last(samples) : VendorPage.of(samples, nextToken.toString());
		};
		return VendorPaginator.fetchAll(pages);
	}

	private static RawMetricSample toSample(Map<String, Object> datapoint) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		for (Object property : list(datapoint.get("properties"))) {
			Map<String, Object> p = map(property);
			if (p.get("name") != null && p.get("value") != null) {
				metrics.put(p.get("name").toString(), p.get("value"));
			}
		}
		return new RawMetricSample(Instant.ofEpochMilli(longValue(datapoint.get("t"), "t")), metrics);
	}
}
