package com.abhishek.smarthome.vendor.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for one vendor's API.
 *
 * @param baseUrl        vendor API root; client paths are resolved relative to it
 * @param connectTimeout TCP connect timeout
 * @param readTimeout    response read timeout
 * @param auth           outbound authentication settings
 */
public record VendorProperties(
		@NotNull URI baseUrl,
		@DefaultValue("1s") Duration connectTimeout,
		@DefaultValue("2s") Duration readTimeout,
		@Valid @NotNull AuthProperties auth) {
}
