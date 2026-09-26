package com.abhishek.smarthome.vendor.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Outbound authentication settings for one vendor.
 *
 * @param type   authentication scheme
 * @param name   header or query parameter name that carries the key
 * @param prefix optional value prefix, e.g. {@code "Bearer "}; only used by {@link AuthType#API_KEY_HEADER}
 * @param apiKey the secret; supplied via environment variables outside local development
 */
public record AuthProperties(
		@NotNull AuthType type,
		@NotBlank String name,
		@Nullable String prefix,
		@NotBlank String apiKey) {

	@Override
	public String toString() {
		return "AuthProperties[type=" + type + ", name=" + name + ", prefix=" + prefix + ", apiKey=****]";
	}
}
