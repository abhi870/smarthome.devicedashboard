package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.entity.DeviceReading;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.entity.DailyMetricRollup;
import com.abhishek.smarthome.reports.enums.AggregationKind;
import com.abhishek.smarthome.reports.repository.DailyMetricRollupRepository;
import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import com.abhishek.smarthome.repository.DeviceReadingRepository;
import com.abhishek.smarthome.service.SwitchState;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Loads readings / rollups and turns them into {@link MetricStatistics}. Runs inside the caller's transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStatisticsService {

	private final DeviceReadingRepository deviceReadingRepository;
	private final DailyMetricRollupRepository dailyMetricRollupRepository;
	private final ReportProperties reportProperties;

	/** Statistics of each metric over {@code [start, end)} from raw readings (one query, plus one per counter metric). */
	public Map<MetricType, MetricStatistics> calculateFromReadings(UUID homeDeviceId, Set<MetricType> metrics,
			Instant start, Instant end) {
		Map<MetricType, List<MetricSample>> samplesByMetric = loadSamples(homeDeviceId, metrics, start, end);
		Map<MetricType, MetricStatistics> statistics = new EnumMap<>(MetricType.class);
		for (MetricType metric : metrics) {
			statistics.put(metric, MetricStatisticsCalculator.calculate(metric, samplesByMetric.get(metric),
					previousCounterSample(homeDeviceId, metric, start), start, end, reportProperties.getMaxSampleGap()));
		}
		return statistics;
	}

	/**
	 * Statistics of {@code [from, to)} per home-local day of {@code zone}. A day fully inside the range uses its stored
	 * rollups where they exist; partial days at the edges, and days (or metrics) without rollups yet, are computed
	 * from raw readings.
	 */
	public Map<LocalDate, Map<MetricType, MetricStatistics>> calculateByDay(UUID homeDeviceId, Set<MetricType> metrics,
			Instant from, Instant to, ZoneId zone) {
		LocalDate firstDay = LocalDate.ofInstant(from, zone);
		LocalDate lastDay = LocalDate.ofInstant(to.minusNanos(1), zone);
		Map<LocalDate, Map<MetricType, MetricStatistics>> rollups = loadRollups(homeDeviceId, metrics, firstDay, lastDay);
		Map<LocalDate, Map<MetricType, MetricStatistics>> byDay = new TreeMap<>();
		for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
			Instant dayStart = day.atStartOfDay(zone).toInstant();
			Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
			Instant start = from.isAfter(dayStart) ? from : dayStart;
			Instant end = to.isBefore(dayEnd) ? to : dayEnd;
			boolean fullDay = start.equals(dayStart) && end.equals(dayEnd);
			byDay.put(day, statisticsOfDay(homeDeviceId, metrics, start, end,
					fullDay ? rollups.getOrDefault(day, Map.of()) : Map.of()));
		}
		return byDay;
	}

	private Map<MetricType, MetricStatistics> statisticsOfDay(UUID homeDeviceId, Set<MetricType> metrics, Instant start,
			Instant end, Map<MetricType, MetricStatistics> storedRollups) {
		Map<MetricType, MetricStatistics> statistics = new EnumMap<>(MetricType.class);
		statistics.putAll(storedRollups);
		Set<MetricType> missing = metrics.stream().filter(metric -> !storedRollups.containsKey(metric))
				.collect(Collectors.toSet());
		if (!missing.isEmpty()) {
			statistics.putAll(calculateFromReadings(homeDeviceId, missing, start, end));
		}
		return statistics;
	}

	private Map<LocalDate, Map<MetricType, MetricStatistics>> loadRollups(UUID homeDeviceId, Set<MetricType> metrics,
			LocalDate firstDay, LocalDate lastDay) {
		Map<LocalDate, Map<MetricType, MetricStatistics>> rollups = new TreeMap<>();
		for (DailyMetricRollup rollup : dailyMetricRollupRepository.findByHomeDeviceIdAndLocalDateBetween(homeDeviceId,
				firstDay, lastDay)) {
			if (metrics.contains(rollup.getMetric())) {
				rollups.computeIfAbsent(rollup.getLocalDate(), day -> new EnumMap<>(MetricType.class))
						.put(rollup.getMetric(), rollup.toStatistics());
			}
		}
		return rollups;
	}

	private Map<MetricType, List<MetricSample>> loadSamples(UUID homeDeviceId, Set<MetricType> metrics, Instant start,
			Instant end) {
		Map<MetricType, List<MetricSample>> samplesByMetric = new EnumMap<>(MetricType.class);
		metrics.forEach(metric -> samplesByMetric.put(metric, new ArrayList<>()));
		for (DeviceReading reading : deviceReadingRepository.findInRange(homeDeviceId, start, end)) {
			List<MetricSample> samples = samplesByMetric.get(reading.getMetric());
			if (samples != null) {
				toSample(reading).ifPresent(samples::add);
			}
		}
		return samplesByMetric;
	}

	/** The last reading before {@code start}, so a counter's increase across the period boundary is counted once. */
	private @Nullable MetricSample previousCounterSample(UUID homeDeviceId, MetricType metric, Instant start) {
		if (AggregationKind.of(metric) != AggregationKind.COUNTER) {
			return null;
		}
		return deviceReadingRepository.findFirstByHomeDeviceIdAndMetricAndTimeLessThanOrderByTimeDesc(homeDeviceId,
				metric, start).flatMap(this::toSample).orElse(null);
	}

	/** Parses a stored value ({@code "1150.5"}, {@code "ON"}); unreadable values are skipped. */
	private Optional<MetricSample> toSample(DeviceReading reading) {
		try {
			double value = reading.getMetric() == MetricType.SWITCH ? SwitchState.parse(reading.getValue())
					: Double.parseDouble(reading.getValue());
			return Optional.of(new MetricSample(reading.getTime(), value));
		}
		catch (IllegalArgumentException e) {
			log.debug("Skipping unreadable {} value '{}' of home device {}", reading.getMetric(), reading.getValue(),
					reading.getHomeDeviceId());
			return Optional.empty();
		}
	}
}
