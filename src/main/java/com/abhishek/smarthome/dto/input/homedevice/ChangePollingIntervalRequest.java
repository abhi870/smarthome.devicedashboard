package com.abhishek.smarthome.dto.input.homedevice;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** Body of {@code PUT /api/v1/smart-home/home-devices/{id}/polling-interval}, e.g. {@code {"pollingIntervalSeconds":120}}. */
public final class ChangePollingIntervalRequest {

	@NotNull
	private final Integer pollingIntervalSeconds;

	@JsonCreator
	public ChangePollingIntervalRequest(@JsonProperty("pollingIntervalSeconds") Integer pollingIntervalSeconds) {
		this.pollingIntervalSeconds = pollingIntervalSeconds;
	}

	public Integer getPollingIntervalSeconds() {
		return pollingIntervalSeconds;
	}
}
