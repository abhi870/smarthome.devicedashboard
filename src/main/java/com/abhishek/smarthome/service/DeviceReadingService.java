package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.reading.SaveDeviceReadingCommand;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.InvalidTimeRangeException;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and queries the metric history of home devices. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceReadingService {

	private final DeviceReadingRepository repository;
	private final HomeDeviceService homeDeviceService;
	private final Clock clock;

	/**
	 * Stores one reading; {@code collectedAt} is now.

	 * @throws com.abhishek.smarthome.exception.HomeDeviceNotFoundException if the home device does
	 * not exist
	 */
	@Transactional
	public DeviceReading save(SaveDeviceReadingCommand command) {
		requireHomeDevice(command.getHomeDeviceId());
		return repository.save(DeviceReading.record(command.getHomeDeviceId(), command.getMetric(), command.getTime(),
				command.getValue(), command.getUnit(), clock.instant()));
	}

	/**
	 * Readings with {@code start <= time < end}, newest first, optionally only one metric.
	 *
	 * @throws InvalidTimeRangeException if {@code start} is not before {@code end}
	 * @throws com.abhishek.smarthome.exception.HomeDeviceNotFoundException if the home device does
	 * not exist
	 */
	public List<DeviceReading> find(UUID homeDeviceId, Instant start, Instant end, @Nullable MetricType metric) {
		if (!start.isBefore(end)) {
			throw new InvalidTimeRangeException(start, end);
		}
		requireHomeDevice(homeDeviceId);
		return metric == null ? repository.findInRange(homeDeviceId, start, end)
				: repository.findInRange(homeDeviceId, metric, start, end);
	}

	private void requireHomeDevice(UUID homeDeviceId) {
		homeDeviceService.get(homeDeviceId);
	}
}
