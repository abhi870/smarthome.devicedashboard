package com.abhishek.smarthome.reports.service;

import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** One numeric reading of a metric: a stored {@code DeviceReading} parsed for aggregation. */
@Getter
@RequiredArgsConstructor
public final class MetricSample {

	private final Instant time;
	private final double value;
}
