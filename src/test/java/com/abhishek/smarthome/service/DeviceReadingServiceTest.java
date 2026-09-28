package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.abhishek.smarthome.dto.input.reading.SaveDeviceReadingCommand;
import com.abhishek.smarthome.dto.output.reading.DeviceReadingResponse;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.HomeDeviceNotFoundException;
import com.abhishek.smarthome.exception.InvalidTimeRangeException;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceReadingServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:05:30Z");
	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();

	private final DeviceReadingRepository deviceReadingRepository = mock(DeviceReadingRepository.class);
	private final HomeDeviceService homeDeviceService = mock(HomeDeviceService.class);
	private final DeviceReadingService deviceReadingService = new DeviceReadingService(deviceReadingRepository, homeDeviceService);

	@Test
	void shouldSaveReadingWithCollectedAtNow() {
		// given
		given(deviceReadingRepository.save(any(DeviceReading.class))).willAnswer(invocation -> invocation.getArgument(0));
		Instant time = Instant.parse("2026-09-27T10:05:00Z");

		// when
		Instant before = Instant.now();
		DeviceReadingResponse saved = deviceReadingService.save(
				new SaveDeviceReadingCommand(HOME_DEVICE_ID, MetricType.ENERGY, time, "12.345", "kWh"));
		Instant after = Instant.now();

		// then
		assertThat(saved.getHomeDeviceId()).isEqualTo(HOME_DEVICE_ID);
		assertThat(saved.getMetric()).isEqualTo(MetricType.ENERGY);
		assertThat(saved.getValue()).isEqualTo("12.345");
		assertThat(saved.getUnit()).isEqualTo("kWh");
		assertThat(saved.getTime()).isEqualTo(time);
		assertThat(saved.getCollectedAt()).isBetween(before, after);
	}

	@Test
	void shouldNotSave_whenHomeDeviceUnknown() {
		// given
		given(homeDeviceService.getHomeDevice(HOME_DEVICE_ID)).willThrow(new HomeDeviceNotFoundException(HOME_DEVICE_ID));

		// when / then
		assertThatThrownBy(() -> deviceReadingService.save(new SaveDeviceReadingCommand(HOME_DEVICE_ID, MetricType.POWER, NOW, "1", "W")))
				.isInstanceOf(HomeDeviceNotFoundException.class);
		then(deviceReadingRepository).shouldHaveNoInteractions();
	}

	@Test
	void shouldRejectRange_whenStartNotBeforeEnd() {
		assertThatThrownBy(() -> deviceReadingService.find(HOME_DEVICE_ID, NOW, NOW, null))
				.isInstanceOf(InvalidTimeRangeException.class);
		assertThatThrownBy(() -> deviceReadingService.find(HOME_DEVICE_ID, NOW, NOW.minusSeconds(1), null))
				.isInstanceOf(InvalidTimeRangeException.class);
		then(deviceReadingRepository).shouldHaveNoInteractions();
	}

	@Test
	void shouldQueryAllMetricsOrOneMetric() {
		// given
		Instant start = NOW.minusSeconds(3600);
		given(deviceReadingRepository.findInRange(HOME_DEVICE_ID, start, NOW)).willReturn(List.of());
		given(deviceReadingRepository.findInRange(HOME_DEVICE_ID, MetricType.POWER, start, NOW)).willReturn(List.of());

		// when
		deviceReadingService.find(HOME_DEVICE_ID, start, NOW, null);
		deviceReadingService.find(HOME_DEVICE_ID, start, NOW, MetricType.POWER);

		// then
		then(deviceReadingRepository).should().findInRange(HOME_DEVICE_ID, start, NOW);
		then(deviceReadingRepository).should().findInRange(HOME_DEVICE_ID, MetricType.POWER, start, NOW);
	}

	@Test
	void shouldSaveOnlyNewReadings_andRecordTheRun() {
		// given: POWER at 10:00 already stored
		Instant from = Instant.parse("2026-09-27T10:00:00Z");
		DeviceReading existing = DeviceReading.record(HOME_DEVICE_ID, MetricType.POWER, from, "1", "W", NOW);
		given(deviceReadingRepository.findInRange(HOME_DEVICE_ID, from, NOW)).willReturn(List.of(existing));
		DeviceReading duplicate = DeviceReading.record(HOME_DEVICE_ID, MetricType.POWER, from, "2", "W", NOW);
		DeviceReading fresh = DeviceReading.record(HOME_DEVICE_ID, MetricType.SWITCH, from, "ON", "on/off", NOW);

		// when
		int stored = deviceReadingService.saveCollected(HOME_DEVICE_ID, List.of(duplicate, fresh), from, NOW);

		// then
		assertThat(stored).isEqualTo(1);
		then(deviceReadingRepository).should().saveAll(List.of(fresh));
		then(homeDeviceService).should().recordRun(HOME_DEVICE_ID, NOW);
	}
}
