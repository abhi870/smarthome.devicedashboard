package com.abhishek.smarthome.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceReadingConverterTest {

	private static final UUID HOME_DEVICE_ID = UUID.randomUUID();
	private static final Instant T0 = Instant.parse("2026-09-26T10:00:00Z");
	private static final Instant COLLECTED = T0.plusSeconds(300);

	private final DeviceReadingConverter deviceReadingConverter = new DeviceReadingConverter();

	private final List<MetricMapping> samsungFridge = List.of(
			MetricMapping.of("switch.switch", MetricType.SWITCH, Conversion.NONE),
			MetricMapping.of("powerConsumptionReport.power", MetricType.POWER, Conversion.NONE),
			MetricMapping.of("powerConsumptionReport.energy", MetricType.ENERGY, Conversion.WH_TO_KWH),
			MetricMapping.of("temperatureMeasurement.temperature", MetricType.TEMPERATURE, Conversion.F_TO_C));

	@Test
	void shouldConvertEveryMappedMetric_inInternalUnits() {
		RawMetricSample sample = new RawMetricSample(T0, Map.of("switch.switch", "on",
				"powerConsumptionReport.power", 110.5, "powerConsumptionReport.energy", 1500.0,
				"temperatureMeasurement.temperature", 212.0));

		List<DeviceReading> readings = deviceReadingConverter.convert(HOME_DEVICE_ID, samsungFridge, List.of(sample), COLLECTED);

		assertThat(readings).extracting(DeviceReading::getMetric, DeviceReading::getValue, DeviceReading::getUnit)
				.containsExactly(
						tuple(MetricType.SWITCH, "ON", "on/off"),
						tuple(MetricType.POWER, "110.5", "W"),
						tuple(MetricType.ENERGY, "1.5", "kWh"),
						tuple(MetricType.TEMPERATURE, "100", "C"));
		assertThat(readings).allSatisfy(reading -> {
			assertThat(reading.getHomeDeviceId()).isEqualTo(HOME_DEVICE_ID);
			assertThat(reading.getTime()).isEqualTo(T0);
			assertThat(reading.getCollectedAt()).isEqualTo(COLLECTED);
		});
	}

	@Test
	void shouldIgnoreUnmappedMetrics_andSkipUnreadableValues() {
		RawMetricSample sample = new RawMetricSample(T0, Map.of("switch.switch", "maybe",
				"powerConsumptionReport.power", 110.5, "someVendorOnlyMetric", 42));

		List<DeviceReading> readings = deviceReadingConverter.convert(HOME_DEVICE_ID, samsungFridge, List.of(sample), COLLECTED);

		assertThat(readings).extracting(DeviceReading::getMetric).containsExactly(MetricType.POWER);
	}

	@Test
	void shouldFormatNumbersPlainly() {
		assertThat(DeviceReadingConverter.format(MetricType.POWER, 1150.0)).isEqualTo("1150");
		assertThat(DeviceReadingConverter.format(MetricType.ENERGY, 0.0012345678)).isEqualTo("0.001235");
		assertThat(DeviceReadingConverter.format(MetricType.TEMPERATURE, 100.00000000000009)).isEqualTo("100");
		assertThat(DeviceReadingConverter.format(MetricType.SWITCH, 0)).isEqualTo("OFF");
	}
}
