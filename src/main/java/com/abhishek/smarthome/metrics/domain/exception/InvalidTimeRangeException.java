package com.abhishek.smarthome.metrics.domain.exception;

import com.abhishek.smarthome.common.error.BadRequestException;
import java.time.Instant;

/** {@code startDate} must be before {@code endDate}. */
public class InvalidTimeRangeException extends BadRequestException {

	public InvalidTimeRangeException(Instant start, Instant end) {
		super("startDate " + start + " must be before endDate " + end);
	}
}
