package com.abhishek.smarthome.vendor.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.abhishek.smarthome.enums.VendorCode;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Each adapter against a mock vendor server: request parameters and flattening. */
class VendorAdapterTests {

	private static final Instant FROM = Instant.parse("2026-09-26T10:00:00Z");
	private static final Instant TO = Instant.parse("2026-09-26T10:03:00Z");

	private RestClient restClient;

	/** A mock vendor server; the client for the adapter under test is bound to it. */
	private MockRestServiceServer serverFor(String baseUrl) {
		RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
		return server;
	}

	@Test
	void samsung_shouldRequestTheRange_andFlattenCapabilities() {
		MockRestServiceServer server = serverFor("http://vendor/api/v1/samsung");
		server.expect(requestTo(Matchers.startsWith("http://vendor/api/v1/samsung/devices/sam-fridge-01/metrics")))
				.andExpect(queryParam("from", "2026-09-26T10:00:00Z")).andExpect(queryParam("to", "2026-09-26T10:03:00Z"))
				.andRespond(withSuccess("""
						{"deviceId":"sam-fridge-01","samples":[
						 {"timestamp":"2026-09-26T10:00:00Z","components":{"main":{
						  "switch":{"switch":{"value":"on"}},
						  "powerConsumptionReport":{"power":{"value":110.5,"unit":"W"},"energy":{"value":1500.0,"unit":"Wh"}},
						  "temperatureMeasurement":{"temperature":{"value":38.3,"unit":"F"}}}}},
						 {"timestamp":"2026-09-26T10:01:00Z","components":{"main":{"switch":{"switch":{"value":"off"}}}}}]}""",
						MediaType.APPLICATION_JSON));

		List<RawMetricSample> samples = new SamsungVendorAdapter(restClient).fetchMetrics("sam-fridge-01", FROM, TO);

		assertThat(samples).hasSize(2);
		assertThat(samples.get(0).getTime()).isEqualTo(FROM);
		assertThat(samples.get(0).getMetrics()).containsEntry("switch.switch", "on")
				.containsEntry("powerConsumptionReport.power", 110.5)
				.containsEntry("temperatureMeasurement.temperature", 38.3);
		assertThat(samples.get(1).getMetrics()).containsOnlyKeys("switch.switch");
		server.verify();
	}

	@Test
	void amazon_shouldRequestOneAppliance_andFlattenProperties() {
		MockRestServiceServer server = serverFor("http://vendor/api/v1/amazon");
		server.expect(requestTo(Matchers.startsWith("http://vendor/api/v1/amazon/devices/amz-ac-01/metrics")))
				.andExpect(queryParam("startTime", String.valueOf(FROM.toEpochMilli())))
				.andExpect(queryParam("endTime", String.valueOf(TO.toEpochMilli())))
				.andRespond(withSuccess("""
						{"applianceId":"amz-ac-01","datapoints":[
						 {"t":1790416800000,"properties":[{"name":"powerState","value":"ON"},
						  {"name":"powerConsumption","value":1.15,"unit":"KILOWATT"}]},
						 {"t":1790416860000,"properties":[{"name":"powerState","value":"OFF"}]}]}""",
						MediaType.APPLICATION_JSON));

		List<RawMetricSample> samples = new AmazonVendorAdapter(restClient).fetchMetrics("amz-ac-01", FROM, TO);

		assertThat(samples).extracting(RawMetricSample::getTime).containsExactly(FROM, FROM.plusSeconds(60));
		assertThat(samples.get(0).getMetrics()).containsEntry("powerState", "ON").containsEntry("powerConsumption", 1.15);
		server.verify();
	}

	@Test
	void cisco_shouldAskForMinutesUntilTo_andDropSamplesAtOrAfterTo() {
		MockRestServiceServer server = serverFor("http://vendor/api/v1/cisco");
		long since = FROM.getEpochSecond();
		server.expect(requestTo(Matchers.startsWith("http://vendor/api/v1/cisco/devices/csc-oven-01/metrics")))
				.andExpect(queryParam("since", String.valueOf(since))).andExpect(queryParam("limit", "4"))
				.andRespond(withSuccess("""
						{"dev":"csc-oven-01","data":[
						  {"ts":%d,"pwr_w":0.0,"kwh_total":12.5,"temp_c":25.0,"state":"IDLE"},
						  {"ts":%d,"pwr_w":0.0,"kwh_total":12.5,"state":"IDLE"},
						  {"ts":%d,"pwr_w":0.0,"kwh_total":12.5,"state":"IDLE"},
						  {"ts":%d,"pwr_w":0.0,"kwh_total":12.5,"state":"IDLE"}]}"""
						.formatted(since, since + 60, since + 120, since + 180), MediaType.APPLICATION_JSON));

		List<RawMetricSample> samples = new CiscoVendorAdapter(restClient).fetchMetrics("csc-oven-01", FROM, TO);

		// 10:03 is at "to" (exclusive) and dropped
		assertThat(samples).extracting(RawMetricSample::getTime)
				.containsExactly(FROM, FROM.plusSeconds(60), FROM.plusSeconds(120));
		assertThat(samples.get(0).getMetrics()).containsEntry("state", "IDLE").containsEntry("temp_c", 25.0)
				.doesNotContainKey("ts");
		server.verify();
	}

	@Test
	void shouldPropagateVendorErrors() {
		MockRestServiceServer server = serverFor("http://vendor/api/v1/cisco");
		server.expect(requestTo(Matchers.containsString("/metrics"))).andRespond(withServerError());

		assertThatThrownBy(() -> new CiscoVendorAdapter(restClient).fetchMetrics("csc-oven-01", FROM, TO))
				.isInstanceOf(RestClientException.class);
	}

	@Test
	void registry_shouldFindAdapterByVendor_andRejectDuplicates() {
		VendorAdapterRegistry vendorAdapterRegistry = new VendorAdapterRegistry(
				List.of(new SamsungVendorAdapter(restClient), new AmazonVendorAdapter(restClient)));

		assertThat(vendorAdapterRegistry.adapterFor(VendorCode.AMAZON)).isInstanceOf(AmazonVendorAdapter.class);
		assertThatThrownBy(() -> vendorAdapterRegistry.adapterFor(VendorCode.CISCO)).hasMessageContaining("No vendor adapter");
		assertThatThrownBy(() -> new VendorAdapterRegistry(
				List.of(new CiscoVendorAdapter(restClient), new CiscoVendorAdapter(restClient))))
				.hasMessageContaining("Duplicate vendor adapters for CISCO");
	}
}
