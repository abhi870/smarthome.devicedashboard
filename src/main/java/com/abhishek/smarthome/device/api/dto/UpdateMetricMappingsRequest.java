package com.abhishek.smarthome.device.api.dto;

import com.abhishek.smarthome.device.domain.entity.MetricMapping;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of {@code PUT /api/v1/smart-home/devices/{id}/mappings}: the complete new set of mappings. */
public final class UpdateMetricMappingsRequest {

	@NotEmpty
	private final List<@Valid @NotNull MetricMappingRequest> mappings;

	@JsonCreator
	public UpdateMetricMappingsRequest(@JsonProperty("mappings") List<MetricMappingRequest> mappings) {
		this.mappings = mappings;
	}

	public List<MetricMapping> toMappings() {
		return mappings.stream().map(MetricMappingRequest::toMapping).toList();
	}

	public List<MetricMappingRequest> getMappings() {
		return mappings;
	}
}
