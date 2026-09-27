package com.abhishek.smarthome.enums;

/** How our backend authenticates outgoing calls to a vendor. */
public enum AuthType {
	/** API key sent in a request header named {@code name}, optionally prefixed (e.g. {@code "Bearer "}). */
	API_KEY_HEADER
}
