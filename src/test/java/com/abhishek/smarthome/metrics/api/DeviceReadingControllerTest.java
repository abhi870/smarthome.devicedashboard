package com.abhishek.smarthome.metrics.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.abhishek.smarthome.common.error.GlobalExceptionHandler;
import com.abhishek.smarthome.homedevice.domain.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.metrics.domain.entity.DeviceReading;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import com.abhishek.smarthome.metrics.domain.exception.InvalidTimeRangeException;
import com.abhishek.smarthome.metrics.domain.service.DeviceReadingService;
import com.abhishek.smarthome.metrics.domain.service.SaveDeviceReadingCommand;
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

@WebMvcTest(DeviceReadingController.class)
@Import(GlobalExceptionHandler.class)
class DeviceReadingControllerTest {

	private static final String BASE = "/api/v1/smart-home/readings";
	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();
	private static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	DeviceReadingService service;

	private String body(String metric, String valueAndUnit) {
		return """
				{"homeDeviceId":"%s","metric":"%s","time":"2026-09-27T10:00:00Z",%s}
				""".formatted(HOME_DEVICE_ID, metric, valueAndUnit);
	}

	@Test
	void shouldStoreValueAndUnit() {
		// given
		given(service.save(any()))
				.willReturn(DeviceReading.record(HOME_DEVICE_ID, MetricType.POWER, T0, "1150.5", "W", T0));

		// when
		var response = assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON)
				.content(body("POWER", "\"value\":\"1150.5\",\"unit\":\"W\"")));

		// then
		response.hasStatus(HttpStatus.CREATED);
		response.bodyJson().extractingPath("$.homeDeviceId").isEqualTo(HOME_DEVICE_ID.toString());
		response.bodyJson().extractingPath("$.metric").isEqualTo("POWER");
		response.bodyJson().extractingPath("$.value").isEqualTo("1150.5");
		response.bodyJson().extractingPath("$.unit").isEqualTo("W");
		ArgumentCaptor<SaveDeviceReadingCommand> command = ArgumentCaptor.forClass(SaveDeviceReadingCommand.class);
		then(service).should().save(command.capture());
		assertThat(command.getValue().getValue()).isEqualTo("1150.5");
		assertThat(command.getValue().getUnit()).isEqualTo("W");
		assertThat(command.getValue().getTime()).isEqualTo(T0);
	}

	@Test
	void shouldStoreSwitchState_andDefaultUnitToMetricUnit() {
		// given
		given(service.save(any()))
				.willReturn(DeviceReading.record(HOME_DEVICE_ID, MetricType.SWITCH, T0, "ON", "on/off", T0));

		// when
		assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON).content(body("SWITCH", "\"value\":\"ON\"")))
				.hasStatus(HttpStatus.CREATED)
				.bodyJson().extractingPath("$.value").isEqualTo("ON");

		// then
		ArgumentCaptor<SaveDeviceReadingCommand> command = ArgumentCaptor.forClass(SaveDeviceReadingCommand.class);
		then(service).should().save(command.capture());
		assertThat(command.getValue().getUnit()).isEqualTo("on/off");
	}

	@Test
	void shouldReturn400_whenFieldsMissingOrValueBlankOrTooLong() {
		assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON).content("{\"metric\":\"POWER\"}"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors.length()").isEqualTo(3);
		assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON).content(body("POWER", "\"value\":\" \"")))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.errors[0].field").isEqualTo("value");
		assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON)
				.content(body("POWER", "\"value\":\"" + "9".repeat(101) + "\"")))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn404_whenHomeDeviceUnknown() {
		// given
		given(service.save(any())).willThrow(new HomeDeviceNotFoundException(HOME_DEVICE_ID));

		// when / then
		assertThat(mvc.post().uri(BASE).contentType(APPLICATION_JSON).content(body("POWER", "\"value\":\"1\"")))
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void shouldReturnReadingsOfDeviceAndMetricBetweenDates() {
		// given
		Instant end = T0.plusSeconds(3600);
		given(service.find(HOME_DEVICE_ID, T0, end, MetricType.TEMPERATURE)).willReturn(List.of(
				DeviceReading.record(HOME_DEVICE_ID, MetricType.TEMPERATURE, T0.plusSeconds(60), "21.0", "C", T0),
				DeviceReading.record(HOME_DEVICE_ID, MetricType.TEMPERATURE, T0, "21.5", "C", T0)));

		// when
		var response = assertThat(mvc.get().uri(BASE).param("homeDeviceId", HOME_DEVICE_ID.toString())
				.param("metric", "TEMPERATURE").param("startDate", "2026-09-27T10:00:00Z")
				.param("endDate", "2026-09-27T11:00:00Z"));

		// then: order as returned by the service (newest first)
		response.hasStatusOk();
		response.bodyJson().extractingPath("$.length()").isEqualTo(2);
		response.bodyJson().extractingPath("$[0].time").isEqualTo("2026-09-27T10:01:00Z");
		response.bodyJson().extractingPath("$[0].value").isEqualTo("21.0");
		response.bodyJson().extractingPath("$[0].unit").isEqualTo("C");
	}

	@Test
	void shouldReturn400_whenParamsMissingOrMalformed() {
		assertThat(mvc.get().uri(BASE).param("homeDeviceId", HOME_DEVICE_ID.toString())
				.param("startDate", "2026-09-27T10:00:00Z")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri(BASE).param("homeDeviceId", HOME_DEVICE_ID.toString()).param("startDate", "yesterday")
				.param("endDate", "2026-09-27T11:00:00Z")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void shouldReturn400_whenStartNotBeforeEnd() {
		// given
		given(service.find(HOME_DEVICE_ID, T0, T0, null)).willThrow(new InvalidTimeRangeException(T0, T0));

		// when / then
		assertThat(mvc.get().uri(BASE).param("homeDeviceId", HOME_DEVICE_ID.toString())
				.param("startDate", "2026-09-27T10:00:00Z").param("endDate", "2026-09-27T10:00:00Z"))
				.hasStatus(HttpStatus.BAD_REQUEST);
	}
}
