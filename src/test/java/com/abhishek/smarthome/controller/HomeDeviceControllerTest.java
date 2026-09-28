package com.abhishek.smarthome.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.dto.output.homedevice.HomeDeviceResponse;
import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.DeviceNotFoundException;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.exception.InvalidPollingIntervalException;
import com.abhishek.smarthome.service.HomeDeviceService;
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
	HomeDeviceService homeDeviceService;

	private final Home home = Home.register("My home", "Asia/Kolkata", NOW);
	private final Vendor amazon = Vendor.register(VendorCode.AMAZON, "Amazon", NOW);
	private final Device device = Device.register(amazon, DeviceType.AC, "AZ-AC-1", "Amazon Smart AC", List.of(), NOW);

	private String body(String extra) {
		return """
				{"homeId":"%s","deviceId":"%s","externalDeviceId":"amz-ac-01","name":"Bedroom AC"%s}
				""".formatted(home.getId(), device.getId(), extra);
	}

	@Test
	void shouldReturn201WithBody_whenRegistered() {
		// given
		HomeDevice homeDevice = HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 60, NOW);
		given(homeDeviceService.register(any())).willReturn(HomeDeviceResponse.from(homeDevice, device, amazon));

		// when
		var response = assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON)
				.content(body(",\"pollingIntervalSeconds\":60")));

		// then
		response.hasStatus(HttpStatus.CREATED).doesNotContainHeader("Location");
		response.bodyJson().extractingPath("$.id").isEqualTo(homeDevice.getId().toString());
		response.bodyJson().extractingPath("$.vendorCode").isEqualTo("AMAZON");
		response.bodyJson().extractingPath("$.deviceType").isEqualTo("AC");
		response.bodyJson().extractingPath("$.pollingIntervalSeconds").isEqualTo(60);
		response.bodyJson().extractingPath("$.enabled").isEqualTo(true);
	}

	@Test
	void shouldDefaultPollingIntervalTo300_whenOmitted() {
		// given
		given(homeDeviceService.register(any()))
				.willReturn(HomeDeviceResponse.from(HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 300, NOW), device,
						amazon));

		// when
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("")))
				.hasStatus(HttpStatus.CREATED);

		// then
		then(homeDeviceService).should().register(argThat(command -> command.getPollingIntervalSeconds() == 300));
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
		given(homeDeviceService.register(any())).willThrow(new DeviceNotFoundException(device.getId()));

		// when / then
		assertThat(mvc.post().uri(BASE + "/register").contentType(APPLICATION_JSON).content(body("")))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturn404_whenHomeDeviceUnknown() {
		// given
		UUID id = UUID.randomUUID();
		given(homeDeviceService.get(id)).willThrow(new HomeDeviceNotFoundException(id));

		// when / then
		assertThat(mvc.get().uri(BASE + "/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
	}
	@Test
	void shouldListHomeDevices_filteredByHome() {
		// given
		given(homeDeviceService.list(home.getId()))
				.willReturn(List.of(HomeDeviceResponse.from(HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 60, NOW), device,
						amazon)));

		// when / then
		var response = assertThat(mvc.get().uri(BASE).param("homeId", home.getId().toString()));
		response.hasStatusOk();
		response.bodyJson().extractingPath("$[0].externalDeviceId").isEqualTo("amz-ac-01");
		response.bodyJson().extractingPath("$[0].homeId").isEqualTo(home.getId().toString());
	}

	@Test
	void shouldListAllHomeDevices_whenNoHomeGiven() {
		// given
		given(homeDeviceService.list(null)).willReturn(List.of());

		// when / then
		assertThat(mvc.get().uri(BASE)).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(0);
	}

	@Test
	void shouldChangePollingInterval_andReturnNextRunAt() {
		// given
		HomeDevice homeDevice = HomeDevice.register(home, device, "amz-ac-01", "Bedroom AC", 60, NOW);
		homeDevice.changePollingInterval(900, NOW);
		given(homeDeviceService.changePollingInterval(homeDevice.getId(), 900)).willReturn(HomeDeviceResponse.from(homeDevice, device, amazon));

		// when / then
		var response = assertThat(mvc.put().uri(BASE + "/{id}/polling-interval", homeDevice.getId())
				.contentType(APPLICATION_JSON).content("{\"pollingIntervalSeconds\":900}"));
		response.hasStatusOk();
		response.bodyJson().extractingPath("$.pollingIntervalSeconds").isEqualTo(900);
		response.bodyJson().extractingPath("$.nextRunAt").isEqualTo("2026-09-27T10:00:00Z");
	}

	@Test
	void shouldReturn400_whenPollingIntervalOutOfRangeOrMissing() {
		// given
		UUID id = UUID.randomUUID();
		given(homeDeviceService.changePollingInterval(id, 10)).willThrow(new InvalidPollingIntervalException(10, 60, 86_400));

		// when / then
		assertThat(mvc.put().uri(BASE + "/{id}/polling-interval", id).contentType(APPLICATION_JSON)
				.content("{\"pollingIntervalSeconds\":10}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.put().uri(BASE + "/{id}/polling-interval", id).contentType(APPLICATION_JSON).content("{}"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}
}
