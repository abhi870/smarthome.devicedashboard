package com.abhishek.smarthome.dto.input.reading;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST /api/v1/smart-home/readings}, e.g.
 * {@code {"homeDeviceId":"…","metric":"POWER","time":"2026-09-27T10:05:00Z","value":"1150.5","unit":"W"}} or
 * {@code {…,"metric":"SWITCH","value":"ON"}}. {@code unit} is optional and defaults to the metric's canonical unit.
 */
@Getter
public final class SaveDeviceReadingRequest {

	@NotNull
	private final UUID homeDeviceId;

	@NotNull
	private final MetricType metric;

	@NotNull
	private final Instant time;

	@NotBlank
	@Size(max = DeviceReading.MAX_VALUE_LENGTH)
	private final String value;

	@Size(max = DeviceReading.MAX_UNIT_LENGTH)
	private final @Nullable String unit;

	@JsonCreator
	public SaveDeviceReadingRequest(@JsonProperty("homeDeviceId") UUID homeDeviceId,
			@JsonProperty("metric") MetricType metric, @JsonProperty("time") Instant time,
			@JsonProperty("value") String value, @JsonProperty("unit") @Nullable String unit) {
		this.homeDeviceId = homeDeviceId;
		this.metric = metric;
		this.time = time;
		this.value = value;
		this.unit = unit;
	}

	public SaveDeviceReadingCommand toCommand() {
		String effectiveUnit = unit == null || unit.isBlank() ? metric.getUnit() : unit.trim();
		return new SaveDeviceReadingCommand(homeDeviceId, metric, time, value.trim(), effectiveUnit);
	}
}
