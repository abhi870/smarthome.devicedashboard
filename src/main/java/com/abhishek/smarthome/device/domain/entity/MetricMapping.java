package com.abhishek.smarthome.device.domain.entity;

import com.abhishek.smarthome.device.domain.exception.InvalidMetricMappingException;
import com.abhishek.smarthome.metrics.domain.entity.Conversion;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import com.abhishek.smarthome.metrics.domain.entity.SwitchState;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

/**
 * How one vendor metric of a catalogue {@link Device} maps to a canonical {@link MetricType}, e.g.
 * {@code room_temp_f → TEMPERATURE via F_TO_C} or {@code activity → SWITCH via NONE}. A value object owned by its
 * device (no id of its own). The conversion must produce the metric's unit, so {@code TEMPERATURE} with
 * {@code KW_TO_W} is rejected.
 */
@Embeddable
@Getter
public class MetricMapping {

	/** Metric name as the vendor reports it (nested vendor payloads use a dotted path). */
	@Column(name = "external_metric", nullable = false, length = 100)
	private String externalMetric;

	@Enumerated(EnumType.STRING)
	@Column(name = "metric", nullable = false, length = 30)
	private MetricType metric;

	@Enumerated(EnumType.STRING)
	@Column(name = "conversion", nullable = false, length = 30)
	private Conversion conversion;

	protected MetricMapping() {
		// for JPA
	}

	/**
	 * @throws InvalidMetricMappingException if {@code conversion} does not produce {@code metric}'s unit
	 */
	public MetricMapping(String externalMetric, MetricType metric, Conversion conversion) {
		if (!conversion.supports(metric)) {
			throw new InvalidMetricMappingException("Conversion " + conversion + " cannot be used for metric " + metric
					+ " of '" + externalMetric + "'");
		}
		this.externalMetric = externalMetric;
		this.metric = metric;
		this.conversion = conversion;
	}

	/**
	 * Raw vendor value (number, numeric text, or for {@code SWITCH} an on/off value) → canonical value in
	 * {@code metric.unit()}.
	 *
	 * @throws IllegalArgumentException if the raw value cannot be read for this metric
	 */
	public double toCanonical(Object rawValue) {
		if (metric == MetricType.SWITCH) {
			return SwitchState.parse(rawValue);
		}
		if (rawValue instanceof Number number) {
			return conversion.apply(number.doubleValue());
		}
		try {
			return conversion.apply(Double.parseDouble(String.valueOf(rawValue).trim()));
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Not a number for " + metric + ": " + rawValue, e);
		}
	}
}
