package com.abhishek.smarthome.vendor.adapter;

import com.abhishek.smarthome.entity.MetricMapping;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * One per-minute sample from a vendor, flattened: metric name as the vendor names it (nested payloads as a dotted
 * path, e.g. {@code powerConsumptionReport.power}) → raw value in the vendor's unit (number, text or boolean).
 * Metric names and units stay vendor-specific; {@code MetricMapping}s translate them later.
 */
@Getter
public final class RawMetricSample {

	private final Instant time;
	private final Map<String, Object> metrics;

	public RawMetricSample(Instant time, Map<String, Object> metrics) {
		this.time = time;
		this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
	}

	@Override
	public String toString() {
		return "RawMetricSample[time=" + time + ", metrics=" + metrics + "]";
	}
}
