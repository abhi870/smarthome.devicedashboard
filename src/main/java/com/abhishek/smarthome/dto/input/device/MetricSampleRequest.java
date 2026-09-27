package com.abhishek.smarthome.dto.input.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** A known conversion pair used to check a mapping when it is saved, e.g. {@code {"external":212,"expected":100}}. */
@Getter
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
}
