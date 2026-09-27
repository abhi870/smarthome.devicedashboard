package com.abhishek.smarthome.dto.input.reading;

import com.abhishek.smarthome.enums.MetricType;
import java.time.Instant;
import java.util.UUID;

/** Input for storing one reading, independent of the HTTP layer (the collector will build these too). */
public final class SaveDeviceReadingCommand {

	private final UUID homeDeviceId;
	private final MetricType metric;
	private final Instant time;
	private final String value;
	private final String unit;

	public SaveDeviceReadingCommand(UUID homeDeviceId, MetricType metric, Instant time, String value, String unit) {
		this.homeDeviceId = homeDeviceId;
		this.metric = metric;
		this.time = time;
		this.value = value;
		this.unit = unit;
	}

	public UUID getHomeDeviceId() {
		return homeDeviceId;
	}

	public MetricType getMetric() {
		return metric;
	}

	public Instant getTime() {
		return time;
	}

	public String getValue() {
		return value;
	}

	public String getUnit() {
		return unit;
	}
}
