package com.abhishek.smarthome.dto.output.reading;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** One stored reading. */
@Getter
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
}
