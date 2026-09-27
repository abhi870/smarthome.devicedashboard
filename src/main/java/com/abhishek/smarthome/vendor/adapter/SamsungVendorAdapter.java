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
 * Samsung: {@code GET /devices/{id}/metrics?from&to} (ISO), page-number paging ({@code page}, {@code size},
 * {@code page.totalPages}). Samples nest readings as {@code components.main.<capability>.<attribute>.value}; they are
 * flattened to {@code <capability>.<attribute>} → value, e.g. {@code powerConsumptionReport.power}, {@code switch.switch}.
 */
@Component
class SamsungVendorAdapter implements VendorAdapter {

	static final int PAGE_SIZE = 500;

	private final VendorClientFactory clients;

	SamsungVendorAdapter(VendorClientFactory clients) {
		this.clients = clients;
	}

	@Override
	public VendorCode vendorCode() {
		return VendorCode.SAMSUNG;
	}

	@Override
	public List<RawMetricSample> fetchMetrics(String externalDeviceId, Instant from, Instant to) {
		RestClient client = clients.clientFor(VendorCode.SAMSUNG);
		PageFetcher<RawMetricSample> pages = cursor -> {
			int page = cursor == null ? 0 : Integer.parseInt(cursor);
			Map<String, Object> body = client.get()
					.uri(uri -> uri.path("/devices/{id}/metrics").queryParam("from", from).queryParam("to", to)
							.queryParam("page", page).queryParam("size", PAGE_SIZE).build(externalDeviceId))
					.retrieve().body(JsonMaps.MAP);
			List<RawMetricSample> samples = new ArrayList<>();
			for (Object sample : list(map(body).get("samples"))) {
				samples.add(toSample(map(sample)));
			}
			long totalPages = longValue(map(map(body).get("page")).get("totalPages"), "page.totalPages");
			return page + 1 < totalPages ? VendorPage.of(samples, String.valueOf(page + 1)) : VendorPage.last(samples);
		};
		return VendorPaginator.fetchAll(pages);
	}

	private static RawMetricSample toSample(Map<String, Object> sample) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		Map<String, Object> main = map(map(sample.get("components")).get("main"));
		main.forEach((capability, attributes) -> map(attributes).forEach((attribute, reading) -> {
			Object value = map(reading).get("value");
			if (value != null) {
				metrics.put(capability + "." + attribute, value);
			}
		}));
		return new RawMetricSample(Instant.parse(String.valueOf(sample.get("timestamp"))), metrics);
	}
}
