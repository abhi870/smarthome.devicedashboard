package com.abhishek.smarthome.dto.output.device;

import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.MetricType;
import java.math.BigDecimal;

/** A stored mapping: the full recipe {@code internal = external × factor + offset}. */
public final class MetricMappingResponse {

	private final String externalMetric;
	private final MetricType metric;
	private final String externalUnit;
	private final String internalUnit;
	private final BigDecimal factor;
	private final BigDecimal offset;

	private MetricMappingResponse(MetricMapping mapping) {
		this.externalMetric = mapping.getExternalMetric();
		this.metric = mapping.getMetric();
		this.externalUnit = mapping.getExternalUnit();
		this.internalUnit = mapping.getInternalUnit();
		this.factor = plain(mapping.getFactor());
		this.offset = plain(mapping.getOffset());
	}

	/** 1000.000000000000000 → 1000, 0.001000000000000 → 0.001 (never 1E+3). */
	private static BigDecimal plain(BigDecimal value) {
		BigDecimal stripped = value.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
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

	public String getExternalUnit() {
		return externalUnit;
	}

	public String getInternalUnit() {
		return internalUnit;
	}

	public BigDecimal getFactor() {
		return factor;
	}

	public BigDecimal getOffset() {
		return offset;
	}
}
