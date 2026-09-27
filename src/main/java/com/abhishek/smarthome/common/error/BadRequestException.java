package com.abhishek.smarthome.common.error;

/** The request is well-formed but breaks a domain rule; rendered as 400 Problem Details. */
public abstract class BadRequestException extends RuntimeException {

	protected BadRequestException(String message) {
		super(message);
	}
}
