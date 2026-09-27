package com.abhishek.smarthome.device.api.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** A known conversion pair used to check a mapping when it is saved, e.g. {@code {"external":212,"expected":100}}. */
public final class MetricSampleRequest {

	@NotNull
	private final Double external;

	@NotNull
	private final Double expected;

	@JsonCreator
	public MetricSampleRequest(@JsonProperty("external") Double external, @JsonProperty("expected") Double expected) {
		this.external = external;
		this.expected = expected;
	}

	public Double getExternal() {
		return external;
	}

	public Double getExpected() {
		return expected;
	}
}
