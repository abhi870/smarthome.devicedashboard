package com.abhishek.smarthome.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeZoneIdValidatorTest {

	private final TimeZoneIdValidator validator = new TimeZoneIdValidator();

	@ParameterizedTest
	@ValueSource(strings = { "Asia/Kolkata", "Europe/Berlin", "America/New_York", "UTC", "+05:30" })
	void shouldAccept_validZoneIds(String zone) {
		assertThat(validator.isValid(zone, null)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  " })
	void shouldAccept_missingValue_soTheDefaultApplies(String zone) {
		assertThat(validator.isValid(zone, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Mars/Base", "asia/kolkata", "IST+1", "Kolkata" })
	void shouldReject_unknownZoneIds(String zone) {
		assertThat(validator.isValid(zone, null)).isFalse();
	}
}
