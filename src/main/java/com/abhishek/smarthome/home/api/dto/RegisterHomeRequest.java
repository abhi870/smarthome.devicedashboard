package com.abhishek.smarthome.home.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /api/v1/smart-home/homes/register}. */
public final class RegisterHomeRequest {

	public static final String DEFAULT_TIMEZONE = "UTC";

	@NotBlank
	private final String name;

	/** Optional IANA time zone id (e.g. {@code Asia/Kolkata}); defaults to {@value #DEFAULT_TIMEZONE}. */
	private final @Nullable String timezone;

	@JsonCreator
	public RegisterHomeRequest(@JsonProperty("name") String name, @JsonProperty("timezone") @Nullable String timezone) {
		this.name = name;
		this.timezone = timezone;
	}

	public String getName() {
		return name;
	}

	public String getTimezoneOrDefault() {
		return timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
	}

	public @Nullable String getTimezone() {
		return timezone;
	}
}
