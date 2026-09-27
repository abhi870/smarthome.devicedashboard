package com.abhishek.smarthome.common.error;

/** A requested resource does not exist; rendered as 404 Problem Details. */
public abstract class NotFoundException extends RuntimeException {

	protected NotFoundException(String message) {
		super(message);
	}
}
