package com.abhishek.smarthome.entity;

import com.abhishek.smarthome.enums.Conversion;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.InvalidMetricMappingException;
import com.abhishek.smarthome.service.SwitchState;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The complete recipe for turning one vendor metric of a catalogue {@link Device} into a smart-home reading:
 * {@code externalMetric} in {@code externalUnit} → {@code metric} in {@code internalUnit} via
 * {@code internal = external × factor + offset}. Everything needed to read a new vendor lives here, as data.
 *
 * <p>Rules (violations → {@link InvalidMetricMappingException}, 400):
 * <ul>
 * <li>{@code internalUnit} must be the metric's canonical unit ({@link MetricType#getUnit()}), so readings of the same
 * metric from different vendors can be summed and averaged;</li>
 * <li>{@code factor} must not be zero;</li>
 * <li>SWITCH values are on/off states, so SWITCH mappings must be the identity (factor 1, offset 0).</li>
 * </ul>
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class MetricMapping {

	private static final int UNIT_LENGTH = 20;

	/** Metric name as the vendor reports it (nested vendor payloads use a dotted path). */
	@Column(name = "external_metric", nullable = false, length = 100)
	private String externalMetric;

	@Enumerated(EnumType.STRING)
	@Column(name = "metric", nullable = false, length = 30)
	private MetricType metric;

	/** Unit the vendor sends, e.g. {@code F}, {@code kW}, {@code Wh}. */
	@Column(name = "external_unit", nullable = false, length = UNIT_LENGTH)
	private String externalUnit;

	/** Unit stored in readings; always {@code metric.getUnit()}. */
	@Column(name = "internal_unit", nullable = false, length = UNIT_LENGTH)
	private String internalUnit;

	@Column(name = "factor", nullable = false, precision = 30, scale = 15)
	private BigDecimal factor;

	@Column(name = "value_offset", nullable = false, precision = 30, scale = 15)
	private BigDecimal offset;

	/**
	 * @throws InvalidMetricMappingException if a rule above is broken
	 */
	public MetricMapping(String externalMetric, MetricType metric, String externalUnit, String internalUnit,
			BigDecimal factor, BigDecimal offset) {
		if (!metric.getUnit().equals(internalUnit)) {
			throw new InvalidMetricMappingException("internalUnit of '" + externalMetric + "' must be '"
					+ metric.getUnit() + "' for metric " + metric + ", not '" + internalUnit + "'");
		}
		if (factor.signum() == 0) {
			throw new InvalidMetricMappingException("factor of '" + externalMetric + "' must not be 0");
		}
		if (metric == MetricType.SWITCH && (factor.compareTo(BigDecimal.ONE) != 0 || offset.signum() != 0)) {
			throw new InvalidMetricMappingException(
					"SWITCH mapping '" + externalMetric + "' holds on/off states; use factor 1 and offset 0");
		}
		this.externalMetric = externalMetric;
		this.metric = metric;
		this.externalUnit = externalUnit;
		this.internalUnit = internalUnit;
		this.factor = factor;
		this.offset = offset;
	}

	/**
	 * A mapping filled in from a preset, e.g. {@code of("room_temp_f", TEMPERATURE, F_TO_C)}.
	 *
	 * @throws InvalidMetricMappingException if the preset does not produce the metric's unit
	 */
	public static MetricMapping of(String externalMetric, MetricType metric, Conversion preset) {
		if (!preset.supports(metric)) {
			throw new InvalidMetricMappingException("Conversion " + preset + " cannot be used for metric " + metric
					+ " of '" + externalMetric + "'");
		}
		return new MetricMapping(externalMetric, metric, preset.getExternalUnit(metric), metric.getUnit(), preset.getFactor(),
				preset.getOffset());
	}

	/**
	 * Raw vendor value (number, numeric text, or for SWITCH an on/off value) → value in {@link #internalUnit}.
	 *
	 * @throws IllegalArgumentException if the raw value cannot be read for this metric
	 */
	public double toCanonical(Object rawValue) {
		if (metric == MetricType.SWITCH) {
			return SwitchState.parse(rawValue);
		}
		double external;
		if (rawValue instanceof Number number) {
			external = number.doubleValue();
		}
		else {
			try {
				external = Double.parseDouble(String.valueOf(rawValue).trim());
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("Not a number for " + metric + ": " + rawValue, e);
			}
		}
		return external * factor.doubleValue() + offset.doubleValue();
	}

	/**
	 * Checks the recipe against a known pair, e.g. 212 °F → 100 °C, allowing for rounding in a typed factor.
	 *
	 * @throws InvalidMetricMappingException if {@code external} does not convert to {@code expected}
	 */
	public void verifySample(double external, double expected) {
		double actual = toCanonical(external);
		double tolerance = 0.01 + Math.abs(expected) * 1e-4;
		if (Math.abs(actual - expected) > tolerance) {
			throw new InvalidMetricMappingException("Sample check failed for '" + externalMetric + "': " + external
					+ " " + externalUnit + " converts to " + actual + " " + internalUnit + ", expected " + expected);
		}
	}
}
