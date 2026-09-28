package com.abhishek.smarthome.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** Checks {@link TimeZoneId}: the value parses with {@link ZoneId#of}. */
public class TimeZoneIdValidator implements ConstraintValidator<TimeZoneId, String> {

	@Override
	public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true;
		}
		try {
			ZoneId.of(value);
			return true;
		}
		catch (DateTimeException e) {
			return false;
		}
	}
}
