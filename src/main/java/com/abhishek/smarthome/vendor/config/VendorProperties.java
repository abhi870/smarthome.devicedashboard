package com.abhishek.smarthome.vendor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Connection settings for one vendor's API. */
public final class VendorProperties {

	/** Vendor API root; client paths are resolved relative to it. */
	@NotNull
	private final URI baseUrl;

	/** TCP connect timeout. */
	private final Duration connectTimeout;

	/** Response read timeout. */
	private final Duration readTimeout;

	/** Outbound authentication settings. */
	@Valid
	@NotNull
	private final AuthProperties auth;

	public VendorProperties(URI baseUrl, @DefaultValue("1s") Duration connectTimeout,
			@DefaultValue("2s") Duration readTimeout, AuthProperties auth) {
		this.baseUrl = baseUrl;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.auth = auth;
	}

	public URI getBaseUrl() {
		return baseUrl;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public AuthProperties getAuth() {
		return auth;
	}

	@Override
	public String toString() {
		return "VendorProperties[baseUrl=" + baseUrl + ", connectTimeout=" + connectTimeout
				+ ", readTimeout=" + readTimeout + ", auth=" + auth + "]";
	}
}
