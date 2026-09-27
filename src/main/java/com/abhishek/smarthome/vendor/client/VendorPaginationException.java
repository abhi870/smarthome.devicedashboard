package com.abhishek.smarthome.vendor.client;

/** Paging did not terminate normally: too many pages, or the vendor returned the same cursor twice. */
public class VendorPaginationException extends RuntimeException {

	public VendorPaginationException(String message) {
		super(message);
	}
}
