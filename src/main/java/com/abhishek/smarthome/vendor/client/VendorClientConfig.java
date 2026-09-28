package com.abhishek.smarthome.vendor.client;

import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.auth.VendorAuthRegistry;
import com.abhishek.smarthome.vendor.config.VendorConfigProvider;
import com.abhishek.smarthome.vendor.config.VendorProperties;
import java.net.http.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} bean per vendor, built at startup from {@code smarthome.vendors.<code>}: base URL,
 * connect/read timeouts and the vendor's auth interceptor from {@link VendorAuthRegistry}. Each vendor adapter injects
 * its own client with {@code @Qualifier} ({@link #SAMSUNG}, {@link #AMAZON}, {@link #CISCO}). A vendor without
 * configuration fails startup when its bean is created. Clients are thread-safe and shared.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class VendorClientConfig {

	public static final String SAMSUNG = "samsungRestClient";
	public static final String AMAZON = "amazonRestClient";
	public static final String CISCO = "ciscoRestClient";

	@Bean(SAMSUNG)
	RestClient samsungRestClient(VendorConfigProvider vendorConfigProvider, VendorAuthRegistry vendorAuthRegistry) {
		return buildClient(VendorCode.SAMSUNG, vendorConfigProvider, vendorAuthRegistry);
	}

	@Bean(AMAZON)
	RestClient amazonRestClient(VendorConfigProvider vendorConfigProvider, VendorAuthRegistry vendorAuthRegistry) {
		return buildClient(VendorCode.AMAZON, vendorConfigProvider, vendorAuthRegistry);
	}

	@Bean(CISCO)
	RestClient ciscoRestClient(VendorConfigProvider vendorConfigProvider, VendorAuthRegistry vendorAuthRegistry) {
		return buildClient(VendorCode.CISCO, vendorConfigProvider, vendorAuthRegistry);
	}

	/**
	 * @throws com.abhishek.smarthome.vendor.config.VendorNotConfiguredException if the vendor has no configuration
	 */
	static RestClient buildClient(VendorCode vendor, VendorConfigProvider vendorConfigProvider,
			VendorAuthRegistry vendorAuthRegistry) {
		VendorProperties vendorProperties = vendorConfigProvider.get(vendor);

		log.info("Vendor client {} -> {} (connect {}, read {}, auth {})", vendor, vendorProperties.getBaseUrl(),
				vendorProperties.getConnectTimeout(), vendorProperties.getReadTimeout(),
				vendorProperties.getAuth().getType());

		return configure(RestClient.builder(), vendorProperties, vendorAuthRegistry.interceptorFor(vendor)).build();
	}

	/** Applies a vendor's settings to a builder. Package-private so tests can bind a mock server afterwards. */
	static RestClient.Builder configure(RestClient.Builder builder, VendorProperties vendorProperties,
			ClientHttpRequestInterceptor authInterceptor) {
		return builder
				.baseUrl(vendorProperties.getBaseUrl().toString())
				.requestFactory(requestFactory(vendorProperties))
				.requestInterceptor(authInterceptor);
	}

	static JdkClientHttpRequestFactory requestFactory(VendorProperties vendorProperties) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(vendorProperties.getConnectTimeout()).build();

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

		requestFactory.setReadTimeout(vendorProperties.getReadTimeout());

		return requestFactory;
	}
}
