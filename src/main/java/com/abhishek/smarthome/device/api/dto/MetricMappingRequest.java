package com.abhishek.smarthome.device.api.dto;

import com.abhishek.smarthome.device.domain.entity.MetricMapping;
import com.abhishek.smarthome.metrics.domain.entity.Conversion;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** One entry of {@code mappings}: {@code {"externalMetric":"room_temp_f","metric":"TEMPERATURE","conversion":"F_TO_C"}}. */
public final class MetricMappingRequest {

	@NotBlank
	@Size(max = 100)
	private final String externalMetric;

	@NotNull
	private final MetricType metric;

	/** Optional; defaults to {@link Conversion#NONE} (vendor already reports the canonical unit). */
	private final @Nullable Conversion conversion;

	@JsonCreator
	public MetricMappingRequest(@JsonProperty("externalMetric") String externalMetric,
			@JsonProperty("metric") MetricType metric, @JsonProperty("conversion") @Nullable Conversion conversion) {
		this.externalMetric = externalMetric;
		this.metric = metric;
		this.conversion = conversion;
	}

	public MetricMapping toMapping() {
		return new MetricMapping(externalMetric, metric, conversion == null ? Conversion.NONE : conversion);
	}

	public String getExternalMetric() {
		return externalMetric;
	}

	public MetricType getMetric() {
		return metric;
	}

	public @Nullable Conversion getConversion() {
		return conversion;
	}
}
