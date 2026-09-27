package com.abhishek.smarthome.service;

import com.abhishek.smarthome.dto.input.reading.SaveDeviceReadingCommand;
import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.exception.InvalidTimeRangeException;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

	private final DeviceReadingRepository deviceReadingRepository;
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
		return deviceReadingRepository.save(DeviceReading.record(command.getHomeDeviceId(), command.getMetric(), command.getTime(),
				command.getValue(), command.getUnit(), clock.instant()));
	}

	/**
	 * Stores the readings of one collection run and records the run on the home device, atomically. Readings that
	 * already exist (same device, metric and time) are skipped, so re-collecting a period is harmless.
	 *
	 * @return the number of readings stored
	 */
	@Transactional
	public int saveCollected(UUID homeDeviceId, List<DeviceReading> readings, Instant from, Instant runAt) {
		Set<String> existing = new HashSet<>();
		for (DeviceReading reading : deviceReadingRepository.findInRange(homeDeviceId, from, runAt)) {
			existing.add(key(reading));
		}
		List<DeviceReading> fresh = readings.stream().filter(reading -> existing.add(key(reading))).toList();
		deviceReadingRepository.saveAll(fresh);
		homeDeviceService.recordRun(homeDeviceId, runAt);
		return fresh.size();
	}

	private static String key(DeviceReading reading) {
		return reading.getMetric() + "|" + reading.getTime();
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
		return metric == null ? deviceReadingRepository.findInRange(homeDeviceId, start, end)
				: deviceReadingRepository.findInRange(homeDeviceId, metric, start, end);
	}

	private void requireHomeDevice(UUID homeDeviceId) {
		homeDeviceService.get(homeDeviceId);
	}
}
