package com.abhishek.smarthome.vendor.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.auth.VendorAuthRegistry;
import com.abhishek.smarthome.vendor.config.AuthProperties;
import com.abhishek.smarthome.vendor.config.AuthType;
import com.abhishek.smarthome.vendor.config.VendorProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * A factory-built client paging through a Samsung-style API ({@code page/size}, {@code page.totalPages}) with
 * {@link VendorPaginator} — the pattern the per-vendor clients will use.
 */
class VendorPagingOverHttpTest {

	private static final String METRICS = "http://localhost:8081/api/v1/samsung/devices/sam-fridge-01/metrics"
			+ "?from=2026-09-26T10:00:00Z&to=2026-09-26T10:03:00Z&size=2&page=";

	@Test
	void shouldCollectSamplesFromAllPages() {
		// given
		VendorProperties samsung = new VendorProperties(URI.create("http://localhost:8081/api/v1/samsung"),
				Duration.ofSeconds(1), Duration.ofSeconds(2),
				new AuthProperties(AuthType.API_KEY_HEADER, "X-API-Key", null, "samsung-key"));
		VendorAuthRegistry auth = mock(VendorAuthRegistry.class);
		given(auth.interceptorFor(VendorCode.SAMSUNG)).willReturn((request, body, execution) -> {
			request.getHeaders().set("X-API-Key", "samsung-key");
			return execution.execute(request, body);
		});
		RestClient.Builder builder = VendorClientFactory.configure(RestClient.builder(), samsung, auth,
				VendorCode.SAMSUNG);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo(METRICS + "0")).andExpect(header("X-API-Key", "samsung-key"))
				.andRespond(withSuccess("""
						{"samples":[{"timestamp":"10:00"},{"timestamp":"10:01"}],"page":{"number":0,"totalPages":2}}""",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo(METRICS + "1"))
				.andRespond(withSuccess("""
						{"samples":[{"timestamp":"10:02"}],"page":{"number":1,"totalPages":2}}""",
						MediaType.APPLICATION_JSON));
		RestClient client = builder.build();

		PageFetcher<JsonNode> samsungPages = cursor -> {
			int page = cursor == null ? 0 : Integer.parseInt(cursor);
			JsonNode body = client.get()
					.uri(uri -> uri.path("/devices/{id}/metrics").queryParam("from", "2026-09-26T10:00:00Z")
							.queryParam("to", "2026-09-26T10:03:00Z").queryParam("size", 2).queryParam("page", page)
							.build("sam-fridge-01"))
					.retrieve().body(JsonNode.class);
			List<JsonNode> samples = StreamSupport.stream(body.get("samples").spliterator(), false).toList();
			boolean more = page + 1 < body.get("page").get("totalPages").asInt();
			return more ? VendorPage.of(samples, String.valueOf(page + 1)) : VendorPage.last(samples);
		};

		// when
		List<JsonNode> samples = VendorPaginator.fetchAll(samsungPages);

		// then
		assertThat(samples).extracting(sample -> sample.get("timestamp").asString())
				.containsExactly("10:00", "10:01", "10:02");
		server.verify();
	}
}
