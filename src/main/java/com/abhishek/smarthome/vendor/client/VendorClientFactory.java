package com.abhishek.smarthome.vendor.client;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.auth.VendorAuthRegistry;
import com.abhishek.smarthome.vendor.config.VendorConfigProvider;
import com.abhishek.smarthome.vendor.config.VendorNotConfiguredException;
import com.abhishek.smarthome.vendor.config.VendorProperties;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Builds one {@link RestClient} per configured vendor at startup from {@link VendorConfigProvider}: base URL,
 * connect/read timeouts and the vendor's outbound auth interceptor from {@link VendorAuthRegistry}. Callers ask for
 * a client by {@link VendorCode} and use paths relative to the vendor's base URL, e.g.
 * {@code clientFor(SAMSUNG).get().uri("/devices")}. Clients are thread-safe and shared.
 */
@Slf4j
@Component
public class VendorClientFactory {

	private final Map<VendorCode, RestClient> clients;

	public VendorClientFactory(VendorConfigProvider config, VendorAuthRegistry authRegistry) {
		Map<VendorCode, RestClient> built = new EnumMap<>(VendorCode.class);
		for (VendorCode vendor : config.configuredVendors()) {
			VendorProperties properties = config.get(vendor);
			built.put(vendor, configure(RestClient.builder(), properties, authRegistry, vendor).build());
			log.info("Vendor client {} -> {} (connect {}, read {}, auth {})", vendor, properties.getBaseUrl(),
					properties.getConnectTimeout(), properties.getReadTimeout(), properties.getAuth().getType());
		}
		this.clients = Collections.unmodifiableMap(built);
	}

	/**
	 * The shared client for a vendor.
	 *
	 * @throws VendorNotConfiguredException if the vendor has no {@code smarthome.vendors.<code>} configuration
	 */
	public RestClient clientFor(VendorCode vendor) {
		RestClient client = clients.get(vendor);
		if (client == null) {
			throw new VendorNotConfiguredException(vendor);
		}
		return client;
	}

	/** Applies a vendor's settings to a builder. Package-private so tests can bind a mock server afterwards. */
	static RestClient.Builder configure(RestClient.Builder builder, VendorProperties properties,
			VendorAuthRegistry authRegistry, VendorCode vendor) {
		return builder
				.baseUrl(properties.getBaseUrl().toString())
				.requestFactory(requestFactory(properties))
				.requestInterceptor(authRegistry.interceptorFor(vendor));
	}

	static JdkClientHttpRequestFactory requestFactory(VendorProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.getReadTimeout());
		return factory;
	}
}
