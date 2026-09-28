package com.abhishek.smarthome.reports.entity;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.enums.ReportStatus;
import com.abhishek.smarthome.reports.enums.ReportType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * A report of one home device over one period: either the {@link ReportType#DAILY} report of a home-local day
 * (written by the daily job) or a {@link ReportType#CUSTOM_RANGE} report requested by a user.
 *
 * <p>Unique {@code (home_device_id, local_date)}: {@code local_date} is only set for DAILY reports (NULLs never
 * collide), so there is at most one DAILY report per device and day — this makes the daily job idempotent.
 * {@code timezone} is a snapshot of the home's timezone when the report was created.
 */
@Entity
@Table(name = "report", uniqueConstraints = @UniqueConstraint(name = "uk_report_daily_device_date",
		columnNames = { "home_device_id", "local_date" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class Report {

	private static final int MAX_ERROR_LENGTH = 1000;

	@Id
	private UUID id;

	@Column(name = "home_device_id", nullable = false, updatable = false)
	private UUID homeDeviceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private ReportType type;

	/** The home-local day of a DAILY report; {@code null} for CUSTOM_RANGE. */
	@Column(name = "local_date", updatable = false)
	private @Nullable LocalDate localDate;

	@Column(name = "period_start", nullable = false, updatable = false)
	private Instant periodStart;

	/** Exclusive. */
	@Column(name = "period_end", nullable = false, updatable = false)
	private Instant periodEnd;

	@Column(nullable = false, length = 64, updatable = false)
	private String timezone;

	/** Comma-separated {@link MetricType} names; read through {@link #getMetrics()}. */
	@Column(name = "metrics", nullable = false, length = 255, updatable = false)
	@Getter(AccessLevel.NONE)
	private String metrics;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReportStatus status;

	@Column(nullable = false)
	private int attempts;

	/** When the daily job retries a FAILED report; {@code null} when no retry is planned. */
	@Column(name = "next_attempt_at")
	private @Nullable Instant nextAttemptAt;

	@Column(length = MAX_ERROR_LENGTH)
	private @Nullable String error;

	/** Average coverage of the metric summaries, 0–100. */
	@Column(name = "coverage_pct")
	private @Nullable Double coveragePct;

	@ElementCollection
	@CollectionTable(name = "report_metric_summary", joinColumns = @JoinColumn(name = "report_id"))
	@OrderBy("metric")
	@Getter(AccessLevel.NONE)
	private List<MetricSummary> summaries = new ArrayList<>();

	/** Daily breakdown, CUSTOM_RANGE reports only. */
	@ElementCollection
	@CollectionTable(name = "report_daily_value", joinColumns = @JoinColumn(name = "report_id"))
	@OrderBy("metric, localDate")
	@Getter(AccessLevel.NONE)
	private List<DailyValue> dailyValues = new ArrayList<>();

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "completed_at")
	private @Nullable Instant completedAt;

	@Version
	private long version;

	/** A DAILY report for {@code localDate} in {@code zone}: the period is that local day (23–25 h around DST). */
	public static Report daily(UUID homeDeviceId, ZoneId zone, LocalDate localDate, Set<MetricType> metrics,
			Instant now) {
		Report report = create(homeDeviceId, ReportType.DAILY, zone, metrics, now);
		report.localDate = localDate;
		report.periodStart = localDate.atStartOfDay(zone).toInstant();
		report.periodEnd = localDate.plusDays(1).atStartOfDay(zone).toInstant();
		return report;
	}

	/** A CUSTOM_RANGE report over {@code [from, to)}; days are split in {@code zone} for the daily breakdown. */
	public static Report customRange(UUID homeDeviceId, ZoneId zone, Instant from, Instant to, Set<MetricType> metrics,
			Instant now) {
		Report report = create(homeDeviceId, ReportType.CUSTOM_RANGE, zone, metrics, now);
		report.periodStart = from;
		report.periodEnd = to;
		return report;
	}

	private static Report create(UUID homeDeviceId, ReportType type, ZoneId zone, Set<MetricType> metrics,
			Instant now) {
		Report report = new Report();
		report.id = UUID.randomUUID();
		report.homeDeviceId = homeDeviceId;
		report.type = type;
		report.timezone = zone.getId();
		report.metrics = join(metrics);
		report.status = ReportStatus.QUEUED;
		report.requestedAt = now;
		return report;
	}

	/** Generation starts (again): counts the attempt and clears a previous error. */
	public void start() {
		status = ReportStatus.RUNNING;
		attempts++;
		error = null;
		nextAttemptAt = null;
	}

	/** Stores the results; replaces those of a previous run. */
	public void complete(List<MetricSummary> newSummaries, List<DailyValue> newDailyValues, Instant now) {
		summaries.clear();
		summaries.addAll(newSummaries);
		dailyValues.clear();
		dailyValues.addAll(newDailyValues);
		coveragePct = averageCoverage(newSummaries);
		status = ReportStatus.SUCCEEDED;
		completedAt = now;
	}

	/**
	 * Records a failed attempt. It is retried after {@code retryDelay × attempts} (linear backoff) until
	 * {@code maxAttempts}; after that {@code nextAttemptAt} stays {@code null} and only a regenerate request helps.
	 */
	public void fail(String reason, Instant now, Duration retryDelay, int maxAttempts) {
		status = ReportStatus.FAILED;
		error = reason.length() > MAX_ERROR_LENGTH ? reason.substring(0, MAX_ERROR_LENGTH) : reason;
		completedAt = now;
		nextAttemptAt = attempts < maxAttempts ? now.plus(retryDelay.multipliedBy(attempts)) : null;
	}

	/** Whether the daily job should try this report again at {@code now}. */
	public boolean isRetryDue(Instant now) {
		return status == ReportStatus.FAILED && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
	}

	public ZoneId getZone() {
		return ZoneId.of(timezone);
	}

	public Set<MetricType> getMetrics() {
		EnumSet<MetricType> set = EnumSet.noneOf(MetricType.class);
		Arrays.stream(metrics.split(",")).filter(name -> !name.isBlank()).map(MetricType::valueOf).forEach(set::add);
		return Collections.unmodifiableSet(set);
	}

	public List<MetricSummary> getSummaries() {
		return Collections.unmodifiableList(summaries);
	}

	public List<DailyValue> getDailyValues() {
		return Collections.unmodifiableList(dailyValues);
	}

	private static String join(Collection<MetricType> metrics) {
		return metrics.stream().sorted().map(MetricType::name).collect(Collectors.joining(","));
	}

	private static @Nullable Double averageCoverage(List<MetricSummary> summaries) {
		return summaries.isEmpty() ? null
				: summaries.stream().mapToDouble(MetricSummary::getCoveragePct).average().orElse(0);
	}
}
