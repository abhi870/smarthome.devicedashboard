package com.abhishek.smarthome.device.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.abhishek.smarthome.device.domain.exception.InvalidMetricMappingException;
import com.abhishek.smarthome.metrics.domain.entity.Conversion;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import com.abhishek.smarthome.vendor.VendorCode;
import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MetricMappingTest {

	private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

	@ParameterizedTest
	@CsvSource({ "TEMPERATURE, KW_TO_W", "POWER, F_TO_C", "ENERGY, KW_TO_W", "SWITCH, F_TO_C", "RUNTIME, WH_TO_KWH" })
	void shouldReject_whenConversionDoesNotProduceMetricUnit(MetricType metric, Conversion conversion) {
		assertThatThrownBy(() -> new MetricMapping("x", metric, conversion))
				.isInstanceOf(InvalidMetricMappingException.class)
				.hasMessageContaining(conversion.name()).hasMessageContaining(metric.name());
	}

	@ParameterizedTest
	@CsvSource({ "TEMPERATURE, F_TO_C", "TEMPERATURE, K_TO_C", "POWER, KW_TO_W", "ENERGY, WH_TO_KWH",
			"RUNTIME, SECONDS_TO_MINUTES", "SWITCH, NONE", "HUMIDITY, NONE" })
	void shouldAccept_whenConversionMatchesMetric(MetricType metric, Conversion conversion) {
		assertThat(new MetricMapping("x", metric, conversion).getConversion()).isEqualTo(conversion);
	}

	@Test
	void shouldConvertNumbersAndNumericText() {
		MetricMapping temperature = new MetricMapping("room_temp_f", MetricType.TEMPERATURE, Conversion.F_TO_C);

		assertThat(temperature.toCanonical(212)).isCloseTo(100, within(1e-9));
		assertThat(temperature.toCanonical("32")).isCloseTo(0, within(1e-9));
		assertThatThrownBy(() -> temperature.toCanonical("warm")).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "on", "ON", "ACTIVE", "true", "1" })
	void shouldReadSwitchOn(String raw) {
		assertThat(new MetricMapping("activity", MetricType.SWITCH, Conversion.NONE).toCanonical(raw)).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "off", "OFF", "IDLE", "false", "0" })
	void shouldReadSwitchOff(String raw) {
		assertThat(new MetricMapping("activity", MetricType.SWITCH, Conversion.NONE).toCanonical(raw)).isEqualTo(0);
	}

	@Test
	void shouldReadSwitchFromBooleanAndNumber_andRejectUnknownWords() {
		MetricMapping state = new MetricMapping("state", MetricType.SWITCH, Conversion.NONE);

		assertThat(state.toCanonical(true)).isEqualTo(1);
		assertThat(state.toCanonical(0)).isEqualTo(0);
		assertThatThrownBy(() -> state.toCanonical("maybe")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectDuplicateExternalMetric_onRegisterAndReplace() {
		Vendor vendor = Vendor.register(VendorCode.CISCO, "Cisco", NOW);
		MetricMapping power = new MetricMapping("pwr_w", MetricType.POWER, Conversion.NONE);
		List<MetricMapping> duplicated = List.of(power, new MetricMapping("pwr_w", MetricType.SWITCH, Conversion.NONE));

		assertThatThrownBy(() -> Device.register(vendor, DeviceType.TV, "CS-TV-9", "Cisco TV", duplicated, NOW))
				.isInstanceOf(InvalidMetricMappingException.class).hasMessageContaining("pwr_w");

		Device device = Device.register(vendor, DeviceType.TV, "CS-TV-9", "Cisco TV", List.of(power), NOW);
		assertThatThrownBy(() -> device.replaceMetricMappings(duplicated))
				.isInstanceOf(InvalidMetricMappingException.class);
		assertThat(device.getMetricMappings()).containsExactly(power);
	}
}
