package com.abhishek.smarthome.service;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.entity.Vendor;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.vendor.adapter.RawMetricSample;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns raw vendor samples into {@link DeviceReading}s using the catalogue device's {@link MetricMapping}s: for every
 * sample and every mapping whose {@code externalMetric} is present, {@code value = external × factor + offset} in the
 * mapping's {@code internalUnit}. Vendor metrics without a mapping are ignored; a value that cannot be read is skipped
 * with a warning so one bad field never loses the rest of the sample.
 */
@Slf4j
@Component
public class DeviceReadingConverter {

	private static final int DECIMALS = 6;

	public List<DeviceReading> convert(UUID homeDeviceId, List<MetricMapping> mappings, List<RawMetricSample> samples,
			Instant collectedAt) {
		List<DeviceReading> readings = new ArrayList<>();
		for (RawMetricSample sample : samples) {
			for (MetricMapping mapping : mappings) {
				Object raw = sample.getMetrics().get(mapping.getExternalMetric());
				if (raw == null) {
					continue;
				}
				try {
					double value = mapping.toCanonical(raw);
					readings.add(DeviceReading.record(homeDeviceId, mapping.getMetric(), sample.getTime(),
							format(mapping.getMetric(), value), mapping.getInternalUnit(), collectedAt));
				}
				catch (IllegalArgumentException e) {
					log.warn("Skipping {} of home device {} at {}: {}", mapping.getExternalMetric(), homeDeviceId,
							sample.getTime(), e.getMessage());
				}
			}
		}
		return readings;
	}

	/** SWITCH → {@code ON}/{@code OFF}; numbers → plain decimal text rounded to 6 places ({@code 100}, {@code 1.15}). */
	static String format(MetricType metric, double value) {
		if (metric == MetricType.SWITCH) {
			return value == SwitchState.ON ? "ON" : "OFF";
		}
		BigDecimal rounded = BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
		return (rounded.scale() < 0 ? rounded.setScale(0) : rounded).toPlainString();
	}
}
