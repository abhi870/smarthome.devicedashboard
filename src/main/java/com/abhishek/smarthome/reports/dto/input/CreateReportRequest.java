package com.abhishek.smarthome.reports.dto.input;

import com.abhishek.smarthome.enums.MetricType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST /api/v1/smart-home/home-devices/{id}/reports}, e.g.
 * {@code {"from":"2026-09-01T00:00:00Z","to":"2026-09-08T00:00:00Z","metrics":["ENERGY","TEMPERATURE"]}}.
 * {@code to} is exclusive; {@code metrics} is optional and defaults to every metric the device maps.
 */
@Getter
public final class CreateReportRequest {

	@NotNull
	private final Instant from;

	@NotNull
	private final Instant to;

	private final @Nullable Set<MetricType> metrics;

	@JsonCreator
	public CreateReportRequest(@JsonProperty("from") Instant from, @JsonProperty("to") Instant to,
			@JsonProperty("metrics") @Nullable Set<MetricType> metrics) {
		this.from = from;
		this.to = to;
		this.metrics = metrics;
	}
}
