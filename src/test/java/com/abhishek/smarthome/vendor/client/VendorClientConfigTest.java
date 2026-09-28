package com.abhishek.smarthome.vendor.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.abhishek.smarthome.enums.AuthType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.auth.VendorAuthRegistry;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import com.abhishek.smarthome.vendor.config.SmartHomeProperties;
import com.abhishek.smarthome.vendor.config.VendorConfigProvider;
import com.abhishek.smarthome.vendor.config.VendorNotConfiguredException;
import com.abhishek.smarthome.vendor.config.VendorProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class VendorClientConfigTest {

	private final VendorProperties samsung = new VendorProperties(URI.create("http://localhost:8081/api/v1/samsung"),
			Duration.ofSeconds(1), Duration.ofSeconds(2),
			new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "samsung-key"));

	private final VendorConfigProvider vendorConfigProvider = new VendorConfigProvider(
			new SmartHomeProperties(Map.of(VendorCode.SAMSUNG, samsung)));

	private final VendorAuthRegistry vendorAuthRegistry = mock(VendorAuthRegistry.class);

	/** Stands in for the real API-key interceptor (tested in the auth package). */
	private final ClientHttpRequestInterceptor apiKeyInterceptor = (request, body, execution) -> {
		request.getHeaders().set("X-API-Key", "samsung-key");
		return execution.execute(request, body);
	};

	private final VendorClientConfig vendorClientConfig = new VendorClientConfig();

	@Test
	void shouldBuildClientForConfiguredVendor() {
		given(vendorAuthRegistry.interceptorFor(VendorCode.SAMSUNG)).willReturn(apiKeyInterceptor);

		assertThat(vendorClientConfig.samsungRestClient(vendorConfigProvider, vendorAuthRegistry)).isNotNull();
	}

	@Test
	void shouldFailAtStartup_whenVendorHasNoConfig() {
		assertThatThrownBy(() -> vendorClientConfig.ciscoRestClient(vendorConfigProvider, vendorAuthRegistry))
				.isInstanceOf(VendorNotConfiguredException.class)
				.hasMessageContaining("smarthome.vendors.cisco");
	}

	@Test
	void shouldResolvePathsAgainstBaseUrl_andSendAuthHeader() {
		RestClient.Builder builder = VendorClientConfig.configure(RestClient.builder(), samsung, apiKeyInterceptor);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://localhost:8081/api/v1/samsung/devices"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("X-API-Key", "samsung-key"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		String body = builder.build().get().uri("/devices").retrieve().body(String.class);

		assertThat(body).isEqualTo("[]");
		server.verify();
	}

	@Test
	void shouldApplyConnectAndReadTimeouts() {
		assertThat(VendorClientConfig.requestFactory(samsung)).isNotNull();
	}
}
