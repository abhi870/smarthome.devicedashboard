package com.abhishek.smarthome.exception;

import com.abhishek.smarthome.common.error.BadRequestException;

/** A polling interval outside the allowed range. */
public class InvalidPollingIntervalException extends BadRequestException {

	public InvalidPollingIntervalException(int seconds, int min, int max) {
		super("pollingIntervalSeconds must be between " + min + " and " + max + ", not " + seconds);
	}
}
