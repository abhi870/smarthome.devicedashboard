package com.abhishek.smarthome.metrics.api.dto;

import com.abhishek.smarthome.metrics.domain.entity.DeviceReading;
import com.abhishek.smarthome.metrics.domain.entity.MetricType;
import java.time.Instant;
import java.util.UUID;

/** One stored reading. */
public final class DeviceReadingResponse {

	private final UUID id;
	private final UUID homeDeviceId;
	private final MetricType metric;
	private final Instant time;
	private final String value;
	private final String unit;
	private final Instant collectedAt;

	private DeviceReadingResponse(DeviceReading reading) {
		this.id = reading.getId();
		this.homeDeviceId = reading.getHomeDeviceId();
		this.metric = reading.getMetric();
		this.time = reading.getTime();
		this.value = reading.getValue();
		this.unit = reading.getUnit();
		this.collectedAt = reading.getCollectedAt();
	}

	public static DeviceReadingResponse from(DeviceReading reading) {
		return new DeviceReadingResponse(reading);
	}

	public UUID getId() {
		return id;
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

	public Instant getCollectedAt() {
		return collectedAt;
	}
}
