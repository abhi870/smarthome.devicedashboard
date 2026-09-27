package com.abhishek.smarthome.dto.input.device;

import com.abhishek.smarthome.entity.MetricMapping;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;

/** Body of {@code PUT /api/v1/smart-home/devices/{id}/mappings}: the complete new set of mappings. */
@Getter
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
}
