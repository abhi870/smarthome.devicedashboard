package com.abhishek.smarthome.homedevice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.device.domain.entity.Device;
import com.abhishek.smarthome.device.domain.exception.DeviceNotFoundException;
import com.abhishek.smarthome.device.domain.entity.DeviceType;
import com.abhishek.smarthome.home.domain.entity.Home;
import com.abhishek.smarthome.homedevice.domain.entity.HomeDevice;
import com.abhishek.smarthome.homedevice.domain.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.homedevice.domain.service.HomeDeviceService;
import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(HomeDeviceController.class)
@Import(GlobalExceptionHandler.class)
class HomeDeviceControllerTest {

	private static final String BASE = "/api/v1/smart-home/home-devices";
	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	HomeDeviceService service;

	private final Home home = Home.register("My home", "Asia/Kolkata", NOW);
	private final Device device = Device.register(Vendor.register(VendorCode.AMAZON, "Amazon", NOW), DeviceType.AC,
			"AZ-AC-1", "Amazon Smart AC", List.of(), NOW);

	private String body(String extra) {
		return """
				{"homeId":"%s","deviceId":"%s","externalDeviceId":"amz-ac-01","name":"Bedroom AC"%s}
				""".formatted(home.getId(), device.getId(), extra);
	}

	@Test
	void shouldReturn201WithLocationAndBody_whenRegistered() {
		// given
		HomeDevice homeDevice = HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 60, NOW);
		given(service.register(any())).willReturn(homeDevice);

		// when
		var response = assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body(",\"pollingIntervalSeconds\":60")));

		// then
		response.hasStatus(HttpStatus.CREATED).hasHeader("Location", "http://localhost" + BASE + "/" + homeDevice.getId());
		response.bodyJson().extractingPath("$.vendorCode").isEqualTo("AMAZON");
		response.bodyJson().extractingPath("$.deviceType").isEqualTo("AC");
		response.bodyJson().extractingPath("$.pollingIntervalSeconds").isEqualTo(60);
		response.bodyJson().extractingPath("$.enabled").isEqualTo(true);
	}

	@Test
	void shouldDefaultPollingIntervalTo300_whenOmitted() {
		// given
		given(service.register(any()))
				.willReturn(HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 300, NOW));

		// when
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("")))
				.hasStatus(HttpStatus.CREATED);

		// then
		then(service).should().register(argThat(command -> command.getPollingIntervalSeconds() == 300));
	}

	@Test
	void shouldReturn400WithFieldErrors_whenBodyEmpty() {
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content("{}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors.length()").isEqualTo(4);
	}

	@Test
	void shouldReturn404_whenCatalogueDeviceUnknown() {
		// given
		given(service.register(any())).willThrow(new DeviceNotFoundException(device.getId()));

		// when / then
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("")))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn404_whenHomeDeviceUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(service.get(id)).willThrow(new HomeDeviceNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri(BASE + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}
	@Test
	void shouldListHomeDevices_filteredByHome() {
		// given
		given(service.list(home.getId()))
				.willReturn(List.of(HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 60, NOW)));

		// when / then
		var response = assertThat(mvc.get().uri(BASE).param("homeId", home.getId().toString()));
		response.hasStatusOk();
		response.bodyJson().extractingPath("$[0].externalDeviceId").isEqualTo("amz-ac-01");
		response.bodyJson().extractingPath("$[0].homeId").isEqualTo(home.getId().toString());
	}

	@Test
	void shouldListAllHomeDevices_whenNoHomeGiven() {
		// given
		given(service.list(null)).willReturn(List.of());

		// when / then
		assertThat(mvc.get().uri(BASE)).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);
	}
}
