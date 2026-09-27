package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.dto.input.reading.SaveDeviceReadingCommand;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.exception.InvalidTimeRangeException;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceReadingServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:05:30Z");
	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();

	private final DeviceReadingRepository repository = mock(DeviceReadingRepository.class);
	private final HomeDeviceService homeDeviceService = mock(HomeDeviceService.class);
	private final DeviceReadingService service = new DeviceReadingService(repository, homeDeviceService,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void shouldSaveReadingWithCollectedAtNow() {
		// given
		given(repository.save(any(DeviceReading.class))).willAnswer(invocation -> invocation.getArgument(0));
		Instant time = Instant.parse("2026-09-27T10:05:00Z");

		// when
		DeviceReading saved = service.save(
				new SaveDeviceReadingCommand(HOME_DEVICE_ID, MetricType.ENERGY, time, "12.345", "kWh"));

		// then
		assertThat(saved.getHomeDeviceId()).isEqualTo(HOME_DEVICE_ID);
		assertThat(saved.getMetric()).isEqualTo(MetricType.ENERGY);
		assertThat(saved.getValue()).isEqualTo("12.345");
		assertThat(saved.getUnit()).isEqualTo("kWh");
		assertThat(saved.getTime()).isEqualTo(time);
		assertThat(saved.getCollectedAt()).isEqualTo(NOW);
	}

	@Test
	void shouldNotSave_whenHomeDeviceUnknown() {
		// given
		given(homeDeviceService.get(HOME_DEVICE_ID)).willThrow(new HomeDeviceNotFoundException(HOME_DEVICE_ID));

		// when / then
		assertThatThrownBy(() -> service.save(new SaveDeviceReadingCommand(HOME_DEVICE_ID, MetricType.POWER, NOW, "1", "W")))
				.isInstanceOf(HomeDeviceNotFoundException.class);
		then(repository).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectRange_whenStartNotBeforeEnd() {
		assertThatThrownBy(() -> service.find(HOME_DEVICE_ID, NOW, NOW, null))
				.isInstanceOf(InvalidTimeRangeException.class);
		assertThatThrownBy(() -> service.find(HOME_DEVICE_ID, NOW, NOW.minusSeconds(1), null))
				.isInstanceOf(InvalidTimeRangeException.class);
		then(repository).shouldHaveNoInteractions();
	}

	@Test
	void shouldQueryAllMetricsOrOneMetric() {
		// given
		Instant start = NOW.minusSeconds(3600);
		given(repository.findInRange(HOME_DEVICE_ID, start, NOW)).willReturn(List.of());
		given(repository.findInRange(HOME_DEVICE_ID, MetricType.POWER, start, NOW)).willReturn(List.of());

		// when
		service.find(HOME_DEVICE_ID, start, NOW, null);
		service.find(HOME_DEVICE_ID, start, NOW, MetricType.POWER);

		// then
		then(repository).should().findInRange(HOME_DEVICE_ID, start, NOW);
		then(repository).should().findInRange(HOME_DEVICE_ID, MetricType.POWER, start, NOW);
	}
}
