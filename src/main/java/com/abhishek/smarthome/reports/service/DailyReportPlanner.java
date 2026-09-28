package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.dto.input.DailyReportTask;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.repository.ReportRepository;
import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import com.abhishek.smarthome.service.DeviceCatalogService;
import com.abhishek.smarthome.service.EntityLookups;
import com.abhishek.smarthome.service.HomeDeviceService;
import com.abhishek.smarthome.service.HomeService;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which DAILY reports are due: for every enabled home device, each home-local day from
 * {@code max(registration day, last finished day - backfillDays + 1)} to the last finished day that has no report
 * yet, or whose FAILED report is due for a retry. A day is finished once the home's local midnight is at least
 * {@code gracePeriod} ago. Fixed number of queries: devices, homes, catalogue devices (+ mappings), existing reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportPlanner {

	private final HomeDeviceService homeDeviceService;
	private final HomeService homeService;
	private final DeviceCatalogService deviceCatalogService;
	private final ReportRepository reportRepository;
	private final ReportProperties reportProperties;

	/** At most {@code batchSize} due device-days, oldest day first per device. */
	@Transactional(readOnly = true)
	public List<DailyReportTask> findDueTasks(Instant now) {
		List<HomeDevice> homeDevices = homeDeviceService.findAllEnabled();
		if (homeDevices.isEmpty()) {
			return List.of();
		}
		Map<UUID, Home> homesById = homeService.getHomesById(EntityLookups.idsOf(homeDevices, HomeDevice::getHomeId));
		Map<UUID, Device> devicesById = deviceCatalogService
				.getDevicesById(EntityLookups.idsOf(homeDevices, HomeDevice::getDeviceId));
		Map<UUID, Map<LocalDate, Report>> existing = existingDailyReports(homeDevices, now);
		List<DailyReportTask> tasks = new ArrayList<>();
		for (HomeDevice homeDevice : homeDevices) {
			Optional<ZoneId> zone = zoneOf(homesById.get(homeDevice.getHomeId()));
			Set<MetricType> metrics = mappedMetrics(devicesById.get(homeDevice.getDeviceId()));
			if (zone.isEmpty() || metrics.isEmpty()) {
				continue;
			}
			Map<LocalDate, Report> reportsByDay = existing.getOrDefault(homeDevice.getId(), Map.of());
			LocalDate lastDay = lastFinishedDay(now, zone.get());
			for (LocalDate day = firstDay(homeDevice, zone.get(), lastDay); !day.isAfter(lastDay); day = day.plusDays(1)) {
				Report report = reportsByDay.get(day);
				if (report == null || report.isRetryDue(now)) {
					tasks.add(new DailyReportTask(homeDevice.getId(), zone.get(), day, metrics));
					if (tasks.size() >= reportProperties.getBatchSize()) {
						return tasks;
					}
				}
			}
		}
		return tasks;
	}

	/**
	 * The home's timezone; empty (the home is skipped, the others still get reports) if the stored id is not valid,
	 * e.g. data written before the API validated it.
	 */
	private static Optional<ZoneId> zoneOf(Home home) {
		try {
			return Optional.of(ZoneId.of(home.getTimezone()));
		}
		catch (DateTimeException e) {
			log.warn("Skipping daily reports of home {}: invalid timezone '{}'", home.getId(), home.getTimezone());
			return Optional.empty();
		}
	}

	/** The latest local day whose end (next midnight) is at least the grace period before {@code now}. */
	LocalDate lastFinishedDay(Instant now, ZoneId zone) {
		return LocalDate.ofInstant(now.minus(reportProperties.getGracePeriod()), zone).minusDays(1);
	}

	private LocalDate firstDay(HomeDevice homeDevice, ZoneId zone, LocalDate lastDay) {
		LocalDate backfillStart = lastDay.minusDays(reportProperties.getBackfillDays() - 1L);
		LocalDate registered = LocalDate.ofInstant(homeDevice.getCreatedAt(), zone);
		return registered.isAfter(backfillStart) ? registered : backfillStart;
	}

	/** Existing DAILY reports in the backfill window (with a day of margin for any timezone), per device and day. */
	private Map<UUID, Map<LocalDate, Report>> existingDailyReports(List<HomeDevice> homeDevices, Instant now) {
		LocalDate since = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(reportProperties.getBackfillDays() + 2L);
		Map<UUID, Map<LocalDate, Report>> existing = new HashMap<>();
		for (Report report : reportRepository.findByTypeAndHomeDeviceIdInAndLocalDateGreaterThanEqual(ReportType.DAILY,
				EntityLookups.idsOf(homeDevices, HomeDevice::getId), since)) {
			existing.computeIfAbsent(report.getHomeDeviceId(), id -> new HashMap<>()).put(report.getLocalDate(), report);
		}
		return existing;
	}

	private static Set<MetricType> mappedMetrics(Device device) {
		return device.getMetricMappings().stream().map(MetricMapping::getMetric).collect(Collectors.toSet());
	}
}
