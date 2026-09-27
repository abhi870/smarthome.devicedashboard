package com.abhishek.smarthome.device.api.dto;

import com.abhishek.smarthome.device.domain.entity.MetricMapping;
import com.abhishek.smarthome.metrics.domain.entity.Conversion;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;

/** A metric mapping of a catalogue device, plus the canonical unit the converted value is in. */
public final class MetricMappingResponse {

	private final String externalMetric;
	private final MetricType metric;
	private final Conversion conversion;
	private final String unit;

	private MetricMappingResponse(MetricMapping mapping) {
		this.externalMetric = mapping.getExternalMetric();
		this.metric = mapping.getMetric();
		this.conversion = mapping.getConversion();
		this.unit = mapping.getMetric().unit();
	}

	public static MetricMappingResponse from(MetricMapping mapping) {
		return new MetricMappingResponse(mapping);
	}

	public String getExternalMetric() {
		return externalMetric;
	}

	public MetricType getMetric() {
		return metric;
	}

	public Conversion getConversion() {
		return conversion;
	}

	public String getUnit() {
		return unit;
	}
}
