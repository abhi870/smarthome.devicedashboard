package com.abhishek.smarthome.vendor.adapter;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/** Builds and sends the GET requests of the vendor adapters, so each adapter only states path and parameters. */
final class VendorRequests {

	private VendorRequests() {
	}

	/**
	 * {@code GET path?query} against the vendor client's base URL, JSON body as a map. {@code uriVariables} fill
	 * {@code {…}} placeholders in {@code path}.
	 */
	static Map<String, Object> getJson(RestClient client, String path, Map<String, Object> query,
			Object... uriVariables) {
		return client.get().uri(uri -> buildUri(uri, path, query, uriVariables)).retrieve().body(JsonMaps.MAP);
	}

	/** Ordered query parameters from alternating names and values; {@code null} values are left out. */
	static Map<String, Object> query(Object... namesAndValues) {
		Map<String, Object> query = new LinkedHashMap<>();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			if (namesAndValues[i + 1] != null) {
				query.put((String) namesAndValues[i], namesAndValues[i + 1]);
			}
		}
		return query;
	}

	private static URI buildUri(UriBuilder uri, String path, Map<String, Object> query,
			Object... uriVariables) {
		uri.path(path);
		query.forEach(uri::queryParam);
		return uri.build(uriVariables);
	}
}
