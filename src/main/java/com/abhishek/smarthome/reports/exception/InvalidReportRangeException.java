package com.abhishek.smarthome.reports.exception;

import com.abhishek.smarthome.common.error.BadRequestException;

/** A report range that is empty, reversed or longer than allowed. */
public class InvalidReportRangeException extends BadRequestException {

	public InvalidReportRangeException(String message) {
		super(message);
	}
}
