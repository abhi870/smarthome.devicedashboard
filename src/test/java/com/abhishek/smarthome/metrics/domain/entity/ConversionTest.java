package com.abhishek.smarthome.metrics.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConversionTest {

	@ParameterizedTest
	@CsvSource({
			"NONE, 21.5, 21.5",
			"F_TO_C, 212, 100",
			"F_TO_C, 32, 0",
			"K_TO_C, 273.15, 0",
			"KW_TO_W, 1.25, 1250",
			"WH_TO_KWH, 1500, 1.5",
			"SECONDS_TO_MINUTES, 90, 1.5",
			"HOURS_TO_MINUTES, 2, 120" })
	void shouldConvertToCanonicalUnit(Conversion conversion, double raw, double expected) {
		assertThat(conversion.apply(raw)).isCloseTo(expected, within(1e-9));
	}
}
