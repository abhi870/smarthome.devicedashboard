package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.entity.Device;
import com.abhishek.smarthome.entity.Home;
import com.abhishek.smarthome.entity.HomeDevice;
import com.abhishek.smarthome.entity.MetricMapping;
import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.dto.output.ReportResponse;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.exception.InvalidReportRangeException;
import com.abhishek.smarthome.reports.exception.ReportNotFoundException;
import com.abhishek.smarthome.reports.exception.UnsupportedReportMetricException;
import com.abhishek.smarthome.reports.repository.ReportRepository;
import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import com.abhishek.smarthome.service.DeviceCatalogService;
import com.abhishek.smarthome.service.HomeDeviceService;
import com.abhishek.smarthome.service.HomeService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reports API: on-demand CUSTOM_RANGE reports, listing and reading reports, regenerating one. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

	/** Default window of {@link #list} when {@code from} is not given. */
	private static final int DEFAULT_LIST_DAYS = 31;

	private final ReportRepository reportRepository;
	private final ReportGenerator reportGenerator;
	private final HomeDeviceService homeDeviceService;
	private final HomeService homeService;
	private final DeviceCatalogService deviceCatalogService;
	private final ReportProperties reportProperties;

	/**
	 * Generates a report of a home device over {@code [from, to)} now and returns it. Full home-local days are read
	 * from the daily rollups, the partial days at the edges from raw readings.
	 *
	 * @param metrics {@code null} or empty = every metric the device maps
	 * @throws com.abhishek.smarthome.exception.HomeDeviceNotFoundException if the home device does not exist
	 * @throws InvalidReportRangeException if {@code from >= to} or the range is longer than {@code maxRangeDays}
	 * @throws UnsupportedReportMetricException if a metric is not mapped for the device
	 */
	@Transactional
	public ReportResponse createCustomRange(UUID homeDeviceId, Instant from, Instant to,
			@Nullable Set<MetricType> metrics) {
		requireValidRange(from, to);
		HomeDevice homeDevice = homeDeviceService.getHomeDevice(homeDeviceId);
		Home home = homeService.getHome(homeDevice.getHomeId());
		Device device = deviceCatalogService.getDevice(homeDevice.getDeviceId());
		Set<MetricType> reportMetrics = resolveMetrics(metrics, mappedMetrics(device));
		Report report = reportGenerator.generateCustomRange(homeDeviceId, ZoneId.of(home.getTimezone()), from, to,
				reportMetrics, Instant.now());
		return ReportResponse.from(report);
	}

	/**
	 * Reports of a home device whose period starts on a home-local day in {@code [from, to]}, newest first.
	 * Defaults: {@code to} = today, {@code from} = 30 days before {@code to}.
	 *
	 * @throws com.abhishek.smarthome.exception.HomeDeviceNotFoundException if the home device does not exist
	 * @throws InvalidReportRangeException if {@code from} is after {@code to}
	 */
	public List<ReportResponse> list(UUID homeDeviceId, @Nullable ReportType type, @Nullable LocalDate from,
			@Nullable LocalDate to) {
		HomeDevice homeDevice = homeDeviceService.getHomeDevice(homeDeviceId);
		ZoneId zone = ZoneId.of(homeService.getHome(homeDevice.getHomeId()).getTimezone());
		LocalDate lastDay = to != null ? to : LocalDate.ofInstant(Instant.now(), zone);
		LocalDate firstDay = from != null ? from : lastDay.minusDays(DEFAULT_LIST_DAYS - 1L);
		if (firstDay.isAfter(lastDay)) {
			throw new InvalidReportRangeException("from " + firstDay + " must not be after to " + lastDay);
		}
		Instant start = firstDay.atStartOfDay(zone).toInstant();
		Instant end = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
		List<Report> reports = type == null
				? reportRepository.findByHomeDeviceIdAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartDesc(
						homeDeviceId, start, end)
				: reportRepository
						.findByHomeDeviceIdAndTypeAndPeriodStartGreaterThanEqualAndPeriodStartLessThanOrderByPeriodStartDesc(
								homeDeviceId, type, start, end);
		return reports.stream().map(ReportResponse::from).toList();
	}

	/**
	 * @throws ReportNotFoundException if no report has this id
	 */
	public ReportResponse get(UUID id) {
		return ReportResponse.from(getReport(id));
	}

	/**
	 * Recomputes a report from the current readings (e.g. after late data), keeping its period and metrics. For a
	 * DAILY report the day's rollups are recomputed too.
	 *
	 * @throws ReportNotFoundException if no report has this id
	 */
	@Transactional
	public ReportResponse regenerate(UUID id) {
		return ReportResponse.from(reportGenerator.regenerate(getReport(id), Instant.now()));
	}

	private Report getReport(UUID id) {
		return reportRepository.findById(id).orElseThrow(() -> new ReportNotFoundException(id));
	}

	private void requireValidRange(Instant from, Instant to) {
		if (!from.isBefore(to)) {
			throw new InvalidReportRangeException("from " + from + " must be before to " + to);
		}
		if (Duration.between(from, to).compareTo(Duration.ofDays(reportProperties.getMaxRangeDays())) > 0) {
			throw new InvalidReportRangeException(
					"Range must be at most " + reportProperties.getMaxRangeDays() + " days, was " + from + " to " + to);
		}
	}

	private static Set<MetricType> resolveMetrics(@Nullable Set<MetricType> requested, Set<MetricType> mapped) {
		if (requested == null || requested.isEmpty()) {
			return mapped;
		}
		Set<MetricType> unsupported = EnumSet.noneOf(MetricType.class);
		requested.stream().filter(metric -> !mapped.contains(metric)).forEach(unsupported::add);
		if (!unsupported.isEmpty()) {
			throw new UnsupportedReportMetricException(unsupported, mapped);
		}
		return EnumSet.copyOf(requested);
	}

	private static Set<MetricType> mappedMetrics(Device device) {
		return device.getMetricMappings().stream().map(MetricMapping::getMetric)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(MetricType.class)));
	}
}
