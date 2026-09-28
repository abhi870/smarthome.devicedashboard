package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.dto.input.DailyReportTask;
import com.abhishek.smarthome.reports.entity.DailyMetricRollup;
import com.abhishek.smarthome.reports.entity.DailyValue;
import com.abhishek.smarthome.reports.entity.MetricSummary;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportType;
import com.abhishek.smarthome.reports.repository.DailyMetricRollupRepository;
import com.abhishek.smarthome.reports.repository.ReportRepository;
import com.abhishek.smarthome.reports.scheduler.ReportProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes reports and stores them. Each public method is one transaction: a DAILY report is written together with
 * its day's rollups, so either both exist or neither does.
 */
@Service
@RequiredArgsConstructor
public class ReportGenerator {

	private final ReportRepository reportRepository;
	private final DailyMetricRollupRepository dailyMetricRollupRepository;
	private final ReportStatisticsService reportStatisticsService;
	private final ReportProperties reportProperties;

	/** Generates the DAILY report of a device-day (creating it, or re-running a FAILED one) with the day's rollups. */
	@Transactional
	public Report generateDaily(DailyReportTask task, Instant now) {
		Report report = findOrCreateDaily(task, now);
		computeDaily(report, now);
		return reportRepository.save(report);
	}

	/** Records that generating a device-day failed; the daily job retries it at {@code nextAttemptAt}. */
	@Transactional
	public Report recordDailyFailure(DailyReportTask task, String error, Instant now) {
		Report report = findOrCreateDaily(task, now);
		report.start();
		report.fail(error, now, reportProperties.getRetryDelay(), reportProperties.getMaxAttempts());
		return reportRepository.save(report);
	}

	/** Generates a CUSTOM_RANGE report synchronously. */
	@Transactional
	public Report generateCustomRange(UUID homeDeviceId, ZoneId zone, Instant from, Instant to, Set<MetricType> metrics,
			Instant now) {
		Report report = Report.customRange(homeDeviceId, zone, from, to, metrics, now);
		computeCustomRange(report, now);
		return reportRepository.save(report);
	}

	/** Recomputes an existing report (e.g. after late readings) with the same period and metrics. */
	@Transactional
	public Report regenerate(Report report, Instant now) {
		if (report.getType() == ReportType.DAILY) {
			computeDaily(report, now);
		}
		else {
			computeCustomRange(report, now);
		}
		return reportRepository.save(report);
	}

	private Report findOrCreateDaily(DailyReportTask task, Instant now) {
		return reportRepository
				.findByTypeAndHomeDeviceIdAndLocalDate(ReportType.DAILY, task.getHomeDeviceId(), task.getLocalDate())
				.orElseGet(() -> Report.daily(task.getHomeDeviceId(), task.getZone(), task.getLocalDate(), task.getMetrics(),
						now));
	}

	/** Rollups of the day from raw readings (replacing earlier ones); the report summarizes them. */
	private void computeDaily(Report report, Instant now) {
		report.start();
		UUID homeDeviceId = report.getHomeDeviceId();
		LocalDate day = Objects.requireNonNull(report.getLocalDate(), "DAILY report without localDate");
		Map<MetricType, MetricStatistics> statistics = reportStatisticsService.calculateFromReadings(homeDeviceId,
				report.getMetrics(), report.getPeriodStart(), report.getPeriodEnd());
		dailyMetricRollupRepository.deleteDay(homeDeviceId, day);
		dailyMetricRollupRepository.saveAll(statistics.values().stream()
				.map(dayStatistics -> DailyMetricRollup.of(homeDeviceId, day, dayStatistics, now)).toList());
		report.complete(statistics.values().stream().map(MetricSummary::from).toList(), List.of(), now);
	}

	/** Per-day statistics (rollups for full days, raw readings for the rest), combined into summaries. */
	private void computeCustomRange(Report report, Instant now) {
		report.start();
		Map<LocalDate, Map<MetricType, MetricStatistics>> byDay = reportStatisticsService.calculateByDay(
				report.getHomeDeviceId(), report.getMetrics(), report.getPeriodStart(), report.getPeriodEnd(),
				report.getZone());
		List<MetricSummary> summaries = new ArrayList<>();
		List<DailyValue> dailyValues = new ArrayList<>();
		for (MetricType metric : report.getMetrics()) {
			List<MetricStatistics> days = byDay.values().stream().map(dayStatistics -> dayStatistics.get(metric))
					.filter(Objects::nonNull).toList();
			summaries.add(MetricSummary.from(MetricStatistics.combine(metric, days)));
			byDay.forEach((day, dayStatistics) -> {
				MetricStatistics statistics = dayStatistics.get(metric);
				if (statistics != null) {
					dailyValues.add(DailyValue.of(metric, day, statistics.getHeadlineValue()));
				}
			});
		}
		report.complete(summaries, dailyValues, now);
	}
}
