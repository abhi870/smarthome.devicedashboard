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

class VendorClientFactoryTest {

	private final VendorProperties samsung = new VendorProperties(URI.create("http://localhost:8081/api/v1/samsung"),
			Duration.ofSeconds(1), Duration.ofSeconds(2),
			new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "samsung-key"));

	private final VendorConfigProvider vendorConfigProvider = new VendorConfigProvider(
			new SmartHomeProperties(Map.of(VendorCode.SAMSUNG, samsung)));

	private final VendorAuthRegistry vendorAuthRegistry = mock(VendorAuthRegistry.class);

	/** Stands in for the real API-key interceptor (tested in the auth package). */
	private final ClientHttpRequestInterceptor apiKey = (request, body, execution) -> {
		request.getHeaders().set("X-API-Key", "samsung-key");
		return execution.execute(request, body);
	};

	@Test
	void shouldBuildOneClientPerConfiguredVendor() {
		// given
		given(vendorAuthRegistry.interceptorFor(VendorCode.SAMSUNG)).willReturn(apiKey);

		// when
		VendorClientFactory vendorClientFactory = new VendorClientFactory(vendorConfigProvider, vendorAuthRegistry);

		// then
		assertThat(vendorClientFactory.clientFor(VendorCode.SAMSUNG)).isNotNull().isSameAs(vendorClientFactory.clientFor(VendorCode.SAMSUNG));
	}

	@Test
	void shouldThrowNotConfigured_whenVendorHasNoConfig() {
		// given
		given(vendorAuthRegistry.interceptorFor(VendorCode.SAMSUNG)).willReturn(apiKey);
		VendorClientFactory vendorClientFactory = new VendorClientFactory(vendorConfigProvider, vendorAuthRegistry);

		// when / then
		assertThatThrownBy(() -> vendorClientFactory.clientFor(VendorCode.CISCO))
				.isInstanceOf(VendorNotConfiguredException.class)
				.hasMessageContaining("smarthome.vendors.cisco");
	}

	@Test
	void shouldResolvePathsAgainstBaseUrl_andSendAuthHeader() {
		// given
		given(vendorAuthRegistry.interceptorFor(VendorCode.SAMSUNG)).willReturn(apiKey);
		RestClient.Builder builder = VendorClientFactory.configure(RestClient.builder(), samsung, vendorAuthRegistry,
				VendorCode.SAMSUNG);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://localhost:8081/api/v1/samsung/devices"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("X-API-Key", "samsung-key"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		// when
		String body = builder.build().get().uri("/devices").retrieve().body(String.class);

		// then
		assertThat(body).isEqualTo("[]");
		server.verify();
	}

	@Test
	void shouldFailFast_whenAuthIsMissingForAConfiguredVendor() {
		// given
		given(vendorAuthRegistry.interceptorFor(VendorCode.SAMSUNG)).willThrow(new VendorNotConfiguredException(VendorCode.SAMSUNG));

		// when / then
		assertThatThrownBy(() -> new VendorClientFactory(vendorConfigProvider, vendorAuthRegistry))
				.isInstanceOf(VendorNotConfiguredException.class);
	}
}
