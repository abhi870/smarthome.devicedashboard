package com.abhishek.smarthome.vendor.config;

/** How our backend authenticates outgoing calls to a vendor. */
public enum AuthType {
	/** API key sent in a request header named {@code name}, optionally prefixed (e.g. {@code "Bearer "}). */
	API_KEY_HEADER,
	/** API key sent as the query parameter {@code name}. */
	API_KEY_QUERY
}
