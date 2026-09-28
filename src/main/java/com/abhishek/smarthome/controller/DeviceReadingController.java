package com.abhishek.smarthome.controller;

import com.abhishek.smarthome.dto.input.reading.SaveDeviceReadingRequest;
import com.abhishek.smarthome.dto.output.reading.DeviceReadingResponse;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.service.DeviceReadingService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Device readings: store one, or read those of a home device (and metric) between two instants. */
@RestController
@RequestMapping("/api/v1/smart-home/readings")
@RequiredArgsConstructor
class DeviceReadingController {

	private final DeviceReadingService deviceReadingService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	DeviceReadingResponse save(@Valid @RequestBody SaveDeviceReadingRequest request) {
		return deviceReadingService.save(request.toCommand());
	}

	/**
	 * Readings of a home device with {@code startDate <= time < endDate} (ISO-8601 instants, e.g.
	 * {@code 2026-09-27T00:00:00Z}), newest first; {@code metric} narrows to one metric.
	 */
	@GetMapping
	List<DeviceReadingResponse> find(@RequestParam UUID homeDeviceId, @RequestParam Instant startDate,
			@RequestParam Instant endDate, @RequestParam(required = false) @Nullable MetricType metric) {
		return deviceReadingService.find(homeDeviceId, startDate, endDate, metric);
	}
}
