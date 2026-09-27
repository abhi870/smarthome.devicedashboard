package com.abhishek.smarthome.metrics.domain.entity;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

/**
 * Presets for common unit conversions, so an admin can write {@code "conversion":"F_TO_C"} instead of typing
 * {@code externalUnit/internalUnit/factor/offset}. A preset only <b>fills in</b> a
 * {@link com.abhishek.smarthome.device.domain.entity.MetricMapping}; the mapping stores the resulting recipe
 * ({@code internal = external × factor + offset}) and the collector only ever uses that.
 */
public enum Conversion {

	/** Vendor already reports the canonical unit (or, for SWITCH, an on/off value). */
	NONE(null, "1", "0", EnumSet.allOf(MetricType.class)),
	F_TO_C("F", "0.555555555555556", "-17.777777777777778", EnumSet.of(MetricType.TEMPERATURE)),
	K_TO_C("K", "1", "-273.15", EnumSet.of(MetricType.TEMPERATURE)),
	KW_TO_W("kW", "1000", "0", EnumSet.of(MetricType.POWER)),
	WH_TO_KWH("Wh", "0.001", "0", EnumSet.of(MetricType.ENERGY)),
	SECONDS_TO_MINUTES("s", "0.016666666666667", "0", EnumSet.of(MetricType.RUNTIME)),
	HOURS_TO_MINUTES("h", "60", "0", EnumSet.of(MetricType.RUNTIME));

	private final String externalUnit;
	private final BigDecimal factor;
	private final BigDecimal offset;
	private final Set<MetricType> supportedMetrics;

	Conversion(String externalUnit, String factor, String offset, Set<MetricType> supportedMetrics) {
		this.externalUnit = externalUnit;
		this.factor = new BigDecimal(factor);
		this.offset = new BigDecimal(offset);
		this.supportedMetrics = supportedMetrics;
	}

	/** Unit the vendor sends; for {@link #NONE} it is the metric's own unit. */
	public String externalUnit(MetricType metric) {
		return externalUnit == null ? metric.unit() : externalUnit;
	}

	public BigDecimal factor() {
		return factor;
	}

	public BigDecimal offset() {
		return offset;
	}

	/** Whether this preset yields the canonical unit of {@code metric}. */
	public boolean supports(MetricType metric) {
		return supportedMetrics.contains(metric);
	}

	/** {@code value × factor + offset}. */
	public double apply(double value) {
		return value * factor.doubleValue() + offset.doubleValue();
	}
}
