package com.abhishek.smarthome.device.domain.exception;

import com.abhishek.smarthome.common.error.BadRequestException;

/** A metric mapping breaks a rule, e.g. a conversion that does not produce the mapped metric's unit. */
public class InvalidMetricMappingException extends BadRequestException {

	public InvalidMetricMappingException(String message) {
		super(message);
	}
}
