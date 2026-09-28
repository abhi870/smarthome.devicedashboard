package com.abhishek.smarthome.reports.dto.output;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.entity.DailyValue;
import com.abhishek.smarthome.reports.entity.Report;
import com.abhishek.smarthome.reports.enums.ReportStatus;
import com.abhishek.smarthome.reports.enums.ReportType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

/** A report with its metric summaries (and, for CUSTOM_RANGE, each metric's daily breakdown). */
@Getter
public final class ReportResponse {

	private final UUID id;
	private final UUID homeDeviceId;
	private final ReportType type;
	private final @Nullable LocalDate localDate;
	private final Instant periodStart;
	private final Instant periodEnd;
	private final String timezone;
	private final Set<MetricType> metrics;
	private final ReportStatus status;
	private final int attempts;
	private final @Nullable Instant nextAttemptAt;
	private final @Nullable String error;
	private final @Nullable Double coveragePct;
	private final List<MetricSummaryResponse> summaries;
	private final Instant requestedAt;
	private final @Nullable Instant completedAt;

	private ReportResponse(Report report) {
		this.id = report.getId();
		this.homeDeviceId = report.getHomeDeviceId();
		this.type = report.getType();
		this.localDate = report.getLocalDate();
		this.periodStart = report.getPeriodStart();
		this.periodEnd = report.getPeriodEnd();
		this.timezone = report.getTimezone();
		this.metrics = report.getMetrics();
		this.status = report.getStatus();
		this.attempts = report.getAttempts();
		this.nextAttemptAt = report.getNextAttemptAt();
		this.error = report.getError();
		this.coveragePct = report.getCoveragePct();
		Map<MetricType, List<DailyValueResponse>> dailyByMetric = report.getDailyValues().stream()
				.collect(Collectors.groupingBy(DailyValue::getMetric,
						Collectors.mapping(DailyValueResponse::from, Collectors.toList())));
		this.summaries = report.getSummaries().stream()
				.map(summary -> MetricSummaryResponse.from(summary,
						dailyByMetric.getOrDefault(summary.getMetric(), List.of())))
				.toList();
		this.requestedAt = report.getRequestedAt();
		this.completedAt = report.getCompletedAt();
	}

	public static ReportResponse from(Report report) {
		return new ReportResponse(report);
	}
}
