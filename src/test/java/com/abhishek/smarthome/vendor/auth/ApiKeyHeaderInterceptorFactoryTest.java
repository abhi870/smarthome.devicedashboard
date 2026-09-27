package com.abhishek.smarthome.vendor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.abhishek.smarthome.enums.AuthType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class ApiKeyHeaderInterceptorFactoryTest {

	private final ApiKeyHeaderInterceptorFactory factory = new ApiKeyHeaderInterceptorFactory();

	@Test
	void shouldHandleApiKeyHeaderType() {
		assertThat(factory.type()).isEqualTo(AuthType.API_KEY_HEADER);
	}

	@Test
	void shouldSendRawKey_whenNoPrefixConfigured() throws IOException {
		// given
		ClientHttpRequestInterceptor interceptor =
				factory.create(VendorCode.SAMSUNG, auth("X-API-Key", null, "samsung-key"));

		// when
		HttpHeaders sent = send(interceptor, new MockClientHttpRequest(HttpMethod.GET, URI.create("http://vendor/devices")));

		// then
		assertThat(sent.getFirst("X-API-Key")).isEqualTo("samsung-key");
	}

	@Test
	void shouldPrependPrefix_whenPrefixConfigured() throws IOException {
		// given
		ClientHttpRequestInterceptor interceptor =
				factory.create(VendorCode.AMAZON, auth("Authorization", "Bearer ", "amazon-key"));

		// when
		HttpHeaders sent = send(interceptor, new MockClientHttpRequest(HttpMethod.GET, URI.create("http://vendor/devices")));

		// then
		assertThat(sent.getFirst("Authorization")).isEqualTo("Bearer amazon-key");
	}

	@Test
	void shouldReplaceExistingHeaderValue_whenHeaderAlreadyPresent() throws IOException {
		// given
		ClientHttpRequestInterceptor interceptor =
				factory.create(VendorCode.CISCO, auth("X-Cisco-Api-Key", null, "cisco-key"));
		MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://vendor/devices"));
		request.getHeaders().add("X-Cisco-Api-Key", "stale");

		// when
		HttpHeaders sent = send(interceptor, request);

		// then
		assertThat(sent.get("X-Cisco-Api-Key")).containsExactly("cisco-key");
	}

	@Test
	void shouldNotExposeKey_whenInterceptorPrinted() {
		// given
		ClientHttpRequestInterceptor interceptor =
				factory.create(VendorCode.AMAZON, auth("Authorization", "Bearer ", "amazon-secret"));

		// when / then
		assertThat(interceptor.toString()).doesNotContain("amazon-secret").contains("Authorization");
	}

	private static HttpHeaders send(ClientHttpRequestInterceptor interceptor, MockClientHttpRequest request)
			throws IOException {
		AtomicReference<HttpHeaders> captured = new AtomicReference<>();
		interceptor.intercept(request, new byte[0], (req, body) -> {
			captured.set(req.getHeaders());
			return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
		});
		return captured.get();
	}

	private static AuthProperties auth(String name, String prefix, String key) {
		return new AuthProperties(AuthType.API_KEY_HEADER, name, prefix, key);
	}
}
