package com.abhishek.smarthome.metrics.domain.entity;

import com.abhishek.smarthome.vendor.domain.entity.Vendor;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * Converts a raw numeric vendor value into the canonical unit of a {@link MetricType}. Each conversion produces
 * exactly one metric's unit (e.g. {@link #F_TO_C} only for {@link MetricType#TEMPERATURE}); {@link #NONE} fits any.
 */
public enum Conversion {

	/** Vendor already reports the canonical unit (or, for {@link MetricType#SWITCH}, an on/off value). */
	NONE(value -> value, EnumSet.allOf(MetricType.class)),
	F_TO_C(value -> (value - 32) * 5 / 9, EnumSet.of(MetricType.TEMPERATURE)),
	K_TO_C(value -> value - 273.15, EnumSet.of(MetricType.TEMPERATURE)),
	KW_TO_W(value -> value * 1000, EnumSet.of(MetricType.POWER)),
	WH_TO_KWH(value -> value / 1000, EnumSet.of(MetricType.ENERGY)),
	SECONDS_TO_MINUTES(value -> value / 60, EnumSet.of(MetricType.RUNTIME)),
	HOURS_TO_MINUTES(value -> value * 60, EnumSet.of(MetricType.RUNTIME));

	private final DoubleUnaryOperator function;
	private final Set<MetricType> supportedMetrics;

	Conversion(DoubleUnaryOperator function, Set<MetricType> supportedMetrics) {
		this.function = function;
		this.supportedMetrics = supportedMetrics;
	}

	public double apply(double value) {
		return function.applyAsDouble(value);
	}

	/** Whether this conversion yields the canonical unit of {@code metric}. */
	public boolean supports(MetricType metric) {
		return supportedMetrics.contains(metric);
	}
}
