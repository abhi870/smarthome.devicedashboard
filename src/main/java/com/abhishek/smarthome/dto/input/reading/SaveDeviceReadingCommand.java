package com.abhishek.smarthome.dto.input.reading;

import com.abhishek.smarthome.enums.MetricType;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Input for storing one reading, independent of the HTTP layer (the collector will build these too). */
@Getter
@RequiredArgsConstructor
public final class SaveDeviceReadingCommand {

	private final UUID homeDeviceId;
	private final MetricType metric;
	private final Instant time;
	private final String value;
	private final String unit;
}
