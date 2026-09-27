package com.abhishek.smarthome.vendor.config;

import com.abhishek.smarthome.enums.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** Outbound authentication settings for one vendor. */
public final class AuthProperties {

	/** Authentication scheme. */
	@NotNull
	private final AuthType type;

	/** Header name that carries the key. */
	@NotBlank
	private final String name;

	/** Optional value prefix, e.g. {@code "Bearer "}. */
	@Nullable
	private final String prefix;

	/** The secret; supplied via environment variables outside local development. */
	@NotBlank
	private final String apiKey;

	public AuthProperties(AuthType type, String name, @Nullable String prefix, String apiKey) {
		this.type = type;
		this.name = name;
		this.prefix = prefix;
		this.apiKey = apiKey;
	}

	public AuthType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public @Nullable String getPrefix() {
		return prefix;
	}

	public String getApiKey() {
		return apiKey;
	}

	@Override
	public String toString() {
		return "AuthProperties[type=" + type + ", name=" + name + ", prefix=" + prefix + ", apiKey=****]";
	}
}
