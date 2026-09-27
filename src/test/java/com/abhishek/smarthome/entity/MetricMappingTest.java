package com.abhishek.smarthome.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.DeviceType;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.enums.VendorCode;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import java.math.BigDecimal;
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
		assertThatThrownBy(() -> MetricMapping.of("x", metric, conversion))
				.isInstanceOf(InvalidMetricMappingException.class)
				.hasMessageContaining(conversion.name()).hasMessageContaining(metric.name());
	}

	@ParameterizedTest
	@CsvSource({ "TEMPERATURE, F_TO_C", "TEMPERATURE, K_TO_C", "POWER, KW_TO_W", "ENERGY, WH_TO_KWH",
			"RUNTIME, SECONDS_TO_MINUTES", "SWITCH, NONE", "HUMIDITY, NONE" })
	void shouldAccept_whenConversionMatchesMetric(MetricType metric, Conversion conversion) {
		MetricMapping mapping = MetricMapping.of("x", metric, conversion);

		assertThat(mapping.getInternalUnit()).isEqualTo(metric.unit());
		assertThat(mapping.getFactor()).isEqualByComparingTo(conversion.factor());
		assertThat(mapping.getOffset()).isEqualByComparingTo(conversion.offset());
	}

	@Test
	void shouldFillRecipeFromPreset() {
		MetricMapping temperature = MetricMapping.of("room_temp_f", MetricType.TEMPERATURE, Conversion.F_TO_C);
		MetricMapping power = MetricMapping.of("pwr_w", MetricType.POWER, Conversion.NONE);

		assertThat(temperature.getExternalUnit()).isEqualTo("F");
		assertThat(temperature.getInternalUnit()).isEqualTo("C");
		assertThat(power.getExternalUnit()).isEqualTo("W");
		assertThat(power.getFactor()).isEqualByComparingTo("1");
		assertThat(power.getOffset()).isEqualByComparingTo("0");
	}

	@Test
	void shouldApplyExplicitFactorAndOffset() {
		MetricMapping milliwatts = new MetricMapping("p_mw", MetricType.POWER, "mW", "W", new BigDecimal("0.001"),
				BigDecimal.ZERO);
		MetricMapping kelvin = new MetricMapping("t_k", MetricType.TEMPERATURE, "K", "C", BigDecimal.ONE,
				new BigDecimal("-273.15"));

		assertThat(milliwatts.toCanonical(1_150_000)).isCloseTo(1150, within(1e-9));
		assertThat(kelvin.toCanonical("300.15")).isCloseTo(27, within(1e-9));
	}

	@Test
	void shouldRejectInternalUnitOtherThanMetricUnit() {
		assertThatThrownBy(() -> new MetricMapping("pwr_kw", MetricType.POWER, "kW", "kW", BigDecimal.ONE,
				BigDecimal.ZERO))
				.isInstanceOf(InvalidMetricMappingException.class)
				.hasMessageContaining("must be 'W' for metric POWER");
	}

	@Test
	void shouldRejectZeroFactor_andNonIdentitySwitch() {
		assertThatThrownBy(() -> new MetricMapping("p", MetricType.POWER, "W", "W", BigDecimal.ZERO, BigDecimal.ZERO))
				.isInstanceOf(InvalidMetricMappingException.class).hasMessageContaining("must not be 0");
		assertThatThrownBy(() -> new MetricMapping("s", MetricType.SWITCH, "on/off", "on/off", BigDecimal.TEN,
				BigDecimal.ZERO))
				.isInstanceOf(InvalidMetricMappingException.class).hasMessageContaining("factor 1 and offset 0");
	}

	@Test
	void shouldPassSampleCheck_withRoundedFactor_andFailOnWrongFactor() {
		MetricMapping rounded = new MetricMapping("t_f", MetricType.TEMPERATURE, "F", "C", new BigDecimal("0.5556"),
				new BigDecimal("-17.7778"));
		MetricMapping wrong = new MetricMapping("e", MetricType.ENERGY, "Wh", "kWh", new BigDecimal("1000"),
				BigDecimal.ZERO);

		rounded.verifySample(212, 100); // 100.0072, within tolerance
		assertThatThrownBy(() -> wrong.verifySample(1500, 1.5))
				.isInstanceOf(InvalidMetricMappingException.class)
				.hasMessageContaining("converts to 1500000.0 kWh, expected 1.5");
	}

	@Test
	void shouldConvertNumbersAndNumericText() {
		MetricMapping temperature = MetricMapping.of("room_temp_f", MetricType.TEMPERATURE, Conversion.F_TO_C);

		assertThat(temperature.toCanonical(212)).isCloseTo(100, within(1e-9));
		assertThat(temperature.toCanonical("32")).isCloseTo(0, within(1e-9));
		assertThatThrownBy(() -> temperature.toCanonical("warm")).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "on", "ON", "ACTIVE", "true", "1" })
	void shouldReadSwitchOn(String raw) {
		assertThat(MetricMapping.of("activity", MetricType.SWITCH, Conversion.NONE).toCanonical(raw)).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "off", "OFF", "IDLE", "false", "0" })
	void shouldReadSwitchOff(String raw) {
		assertThat(MetricMapping.of("activity", MetricType.SWITCH, Conversion.NONE).toCanonical(raw)).isEqualTo(0);
	}

	@Test
	void shouldReadSwitchFromBooleanAndNumber_andRejectUnknownWords() {
		MetricMapping state = MetricMapping.of("state", MetricType.SWITCH, Conversion.NONE);

		assertThat(state.toCanonical(true)).isEqualTo(1);
		assertThat(state.toCanonical(0)).isEqualTo(0);
		assertThatThrownBy(() -> state.toCanonical("maybe")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectDuplicateExternalMetric_onRegisterAndReplace() {
		Vendor vendor = Vendor.register(VendorCode.CISCO, "Cisco", NOW);
		MetricMapping power = MetricMapping.of("pwr_w", MetricType.POWER, Conversion.NONE);
		List<MetricMapping> duplicated = List.of(power, MetricMapping.of("pwr_w", MetricType.SWITCH, Conversion.NONE));

		assertThatThrownBy(() -> Device.register(vendor, DeviceType.TV, "CS-TV-9", "Cisco TV", duplicated, NOW))
				.isInstanceOf(InvalidMetricMappingException.class).hasMessageContaining("pwr_w");

		Device device = Device.register(vendor, DeviceType.TV, "CS-TV-9", "Cisco TV", List.of(power), NOW);
		assertThatThrownBy(() -> device.replaceMetricMappings(duplicated))
				.isInstanceOf(InvalidMetricMappingException.class);
		assertThat(device.getMetricMappings()).containsExactly(power);
	}
}
