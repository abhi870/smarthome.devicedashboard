package com.abhishek.smarthome.device.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.device.domain.entity.Device;
import com.abhishek.smarthome.device.domain.service.DeviceCatalogService;
import com.abhishek.smarthome.device.domain.exception.DeviceNotFoundException;
import com.abhishek.smarthome.device.domain.entity.DeviceType;
import com.abhishek.smarthome.device.domain.entity.MetricMapping;
import com.abhishek.smarthome.device.domain.service.RegisterDeviceCommand;
import com.abhishek.smarthome.metrics.domain.entity.Conversion;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DeviceController.class)
@Import(GlobalExceptionHandler.class)
class DeviceControllerTest {

	private static final String BASE = "/api/v1/smart-home/devices";
	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	DeviceCatalogService service;

	private final Vendor amazon = Vendor.register(VendorCode.AMAZON, "Amazon", NOW);
	private final List<MetricMapping> mappings = List.of(
			MetricMapping.of("room_temp_f", MetricType.TEMPERATURE, Conversion.F_TO_C),
			MetricMapping.of("pwr_kw", MetricType.POWER, Conversion.KW_TO_W));

	private String body(String mappingsJson) {
		return """
				{"vendorId":"%s","deviceType":"AC","model":"AZ-AC-1","name":"Amazon Smart AC","mappings":%s}
				""".formatted(amazon.getId(), mappingsJson);
	}

	@Test
	void shouldReturn201WithMappings_whenRegistered() {
		// given
		Device device = Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC", mappings, NOW);
		given(service.register(any())).willReturn(device);

		// when
		var response = assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("""
				[{"externalMetric":"room_temp_f","metric":"TEMPERATURE","conversion":"F_TO_C"},
				 {"externalMetric":"pwr_kw","metric":"POWER","conversion":"KW_TO_W"}]""")));

		// then
		response.hasStatus(HttpStatus.CREATED).hasHeader("Location", "http://localhost" + BASE + "/" + device.getId());
		response.bodyJson().extractingPath("$.vendorCode").isEqualTo("AMAZON");
		response.bodyJson().extractingPath("$.mappings[0].externalMetric").isEqualTo("room_temp_f");
		response.bodyJson().extractingPath("$.mappings[0].externalUnit").isEqualTo("F");
		response.bodyJson().extractingPath("$.mappings[0].internalUnit").isEqualTo("C");
		response.bodyJson().extractingPath("$.mappings[1].factor").isEqualTo(1000);
		response.bodyJson().extractingPath("$.mappings[1].internalUnit").isEqualTo("W");
	}

	@Test
	void shouldDefaultConversionToNone_whenOmitted() {
		// given
		given(service.register(any())).willReturn(Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC",
				List.of(MetricMapping.of("temperature", MetricType.TEMPERATURE, Conversion.NONE)), NOW));

		// when
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body("[{\"externalMetric\":\"temperature\",\"metric\":\"TEMPERATURE\"}]")))
				.hasStatus(HttpStatus.CREATED);

		// then
		ArgumentCaptor<RegisterDeviceCommand> command = ArgumentCaptor.forClass(RegisterDeviceCommand.class);
		then(service).should().register(command.capture());
		assertThat(command.getValue().getMetricMappings()).singleElement()
				.satisfies(mapping -> {
					assertThat(mapping.getFactor()).isEqualByComparingTo("1");
					assertThat(mapping.getOffset()).isEqualByComparingTo("0");
					assertThat(mapping.getExternalUnit()).isEqualTo("C");
				});
	}

	@Test
	void shouldReturn400_whenMappingsEmpty() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("[]")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors[0].field").isEqualTo("mappings");
	}

	@Test
	void shouldReturn400_whenMappingIncomplete() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body("[{\"conversion\":\"F_TO_C\"}]")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors.length()").isEqualTo(2);
	}

	@Test
	void shouldReturn400_whenConversionUnknown() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body("[{\"externalMetric\":\"t\",\"metric\":\"TEMPERATURE\",\"conversion\":\"C_TO_X\"}]")))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldStoreExplicitRecipe_andCheckSample() {
		// given
		given(service.register(any())).willReturn(Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC",
				mappings, NOW));
		String recipe = """
				[{"externalMetric":"p_mw","metric":"POWER","externalUnit":"mW","internalUnit":"W","factor":0.001,
				  "offset":0,"sample":{"external":1150000,"expected":1150}}]""";

		// when
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body(recipe)))
				.hasStatus(HttpStatus.CREATED);

		// then
		ArgumentCaptor<RegisterDeviceCommand> command = ArgumentCaptor.forClass(RegisterDeviceCommand.class);
		then(service).should().register(command.capture());
		assertThat(command.getValue().getMetricMappings()).singleElement().satisfies(mapping -> {
			assertThat(mapping.getExternalUnit()).isEqualTo("mW");
			assertThat(mapping.getFactor()).isEqualByComparingTo("0.001");
		});
	}

	@Test
	void shouldReturn400_whenInternalUnitIsNotTheMetricUnit() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("""
				[{"externalMetric":"pwr_kw","metric":"POWER","externalUnit":"kW","internalUnit":"kW","factor":1}]""")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").asString().contains("must be 'W' for metric POWER");
		then(service).shouldHaveNoInteractions();
	}

	@Test
	void shouldReturn400_whenSampleCheckFails() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("""
				[{"externalMetric":"e_wh","metric":"ENERGY","externalUnit":"Wh","factor":1000,
				  "sample":{"external":1500,"expected":1.5}}]""")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").asString().contains("Sample check failed");
	}

	@Test
	void shouldReturn400_whenPresetMixedWithExplicitFactor() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("""
				[{"externalMetric":"t","metric":"TEMPERATURE","conversion":"F_TO_C","factor":2}]""")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").asString().contains("not both");
	}

	@Test
	void shouldReturn400_whenConversionDoesNotMatchMetric() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body("[{\"externalMetric\":\"t\",\"metric\":\"TEMPERATURE\",\"conversion\":\"KW_TO_W\"}]")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail")
				.isEqualTo("Conversion KW_TO_W cannot be used for metric TEMPERATURE of 't'");
		then(service).shouldHaveNoInteractions();
	}

	@Test
	void shouldReplaceMappings_whenPut() {
		// given
		Device device = Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC", mappings, NOW);
		device.replaceMetricMappings(List.of(MetricMapping.of("powerState", MetricType.SWITCH, Conversion.NONE)));
		given(service.replaceMetricMappings(eq(device.getId()), any())).willReturn(device);

		// when
		var response = assertThat(mvc.put().uri(BASE + "/{id}/mappings", device.getId()).contentType(APPLICATION_JSON)
				.content("{\"mappings\":[{\"externalMetric\":\"powerState\",\"metric\":\"SWITCH\"}]}"));

		// then
		response.hasStatusOk();
		response.bodyJson().extractingPath("$.mappings.length()").isEqualTo(1);
		response.bodyJson().extractingPath("$.mappings[0].metric").isEqualTo("SWITCH");
		response.bodyJson().extractingPath("$.mappings[0].internalUnit").isEqualTo("on/off");
	}

	@Test
	void shouldReturn400_whenPutMappingsEmpty() {
		assertThat(mvc.put().uri(BASE + "/{id}/mappings", UUID.randomUUID()).contentType(APPLICATION_JSON)
				.content("{\"mappings\":[]}"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn404_whenPutMappingsForUnknownDevice() {
		// given
		UUID id = UUID.randomUUID();
		given(service.replaceMetricMappings(eq(id), any())).willThrow(new DeviceNotFoundException(id));

		// when / then
		assertThat(mvc.put().uri(BASE + "/{id}/mappings", id).contentType(APPLICATION_JSON)
				.content("{\"mappings\":[{\"externalMetric\":\"pwr\",\"metric\":\"POWER\"}]}"))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldListDevices_filteredByVendorAndType() {
		// given
		given(service.list(amazon.getId(), DeviceType.AC))
				.willReturn(List.of(Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC", mappings, NOW)));

		// when / then
		assertThat(mvc.get().uri(BASE).param("vendorId", amazon.getId().toString()).param("deviceType", "AC"))
				.hasStatusOk()
				.bodyJson().extractingPath("$[0].mappings.length()").isEqualTo(2);
	}

	@Test
	void shouldReturn404_whenDeviceUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(service.get(id)).willThrow(new DeviceNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri(BASE + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}
}
