package com.abhishek.smarthome.common.error;

/** The request conflicts with the current state (e.g. a duplicate); rendered as 409 Problem Details. */
public abstract class ConflictException extends RuntimeException {

	protected ConflictException(String message) {
		super(message);
	}
}
