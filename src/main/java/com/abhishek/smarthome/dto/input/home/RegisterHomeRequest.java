package com.abhishek.smarthome.dto.input.home;

import com.abhishek.smarthome.common.validation.TimeZoneId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** Body of {@code POST /api/v1/smart-home/homes/register}. */
@Getter
public final class RegisterHomeRequest {

	public static final String DEFAULT_TIMEZONE = "UTC";

	@NotBlank
	private final String name;

	/**
	 * Optional IANA time zone id (e.g. {@code Asia/Kolkata}); defaults to {@value #DEFAULT_TIMEZONE}. Validated here
	 * because report day boundaries are computed in it — an unknown id would break the daily report job.
	 */
	@TimeZoneId
	@Size(max = 64)
	private final @Nullable String timezone;

	@JsonCreator
	public RegisterHomeRequest(@JsonProperty("name") String name, @JsonProperty("timezone") @Nullable String timezone) {
		this.name = name;
		this.timezone = timezone;
	}

	public String getTimezoneOrDefault() {
		return timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
	}
}
