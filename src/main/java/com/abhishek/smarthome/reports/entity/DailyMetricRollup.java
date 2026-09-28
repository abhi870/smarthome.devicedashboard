package com.abhishek.smarthome.reports.entity;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.service.MetricStatistics;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Read model: the statistics of one metric of one home device on one home-local day, computed from raw readings by
 * the daily report job. Custom-range reports read these for full days instead of re-scanning ~1440 readings per
 * metric and day. Unique {@code (home_device_id, local_date, metric)}; replaced when the day is regenerated.
 *
 * <p>{@link Persistable} so {@code saveAll} inserts directly (batched) instead of merging each row (the id is
 * assigned in {@link #of}).
 */
@Entity
@Table(name = "daily_metric_rollup", uniqueConstraints = @UniqueConstraint(name = "uk_daily_metric_rollup",
		columnNames = { "home_device_id", "local_date", "metric" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class DailyMetricRollup implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "home_device_id", nullable = false, updatable = false)
	private UUID homeDeviceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30, updatable = false)
	private MetricType metric;

	/** The day in the home's timezone. */
	@Column(name = "local_date", nullable = false, updatable = false)
	private LocalDate localDate;

	@Column(name = "sample_count", nullable = false, updatable = false)
	private long sampleCount;

	@Column(name = "min_value", updatable = false)
	private @Nullable Double min;

	@Column(name = "max_value", updatable = false)
	private @Nullable Double max;

	/** Time-weighted average. */
	@Column(name = "avg_value", updatable = false)
	private @Nullable Double avg;

	@Column(name = "first_reading_value", updatable = false)
	private @Nullable Double firstValue;

	@Column(name = "first_reading_time", updatable = false)
	private @Nullable Instant firstTime;

	@Column(name = "last_reading_value", updatable = false)
	private @Nullable Double lastValue;

	@Column(name = "last_reading_time", updatable = false)
	private @Nullable Instant lastTime;

	/** Energy kWh / on-minutes / counter increase, by metric; {@code null} for gauges. */
	@Column(name = "total_value", updatable = false)
	private @Nullable Double total;

	/** Seconds of the day covered by readings (each reading counts until the next one, at most the max gap). */
	@Column(name = "covered_seconds", nullable = false, updatable = false)
	private long coveredSeconds;

	/** Length of the local day: 86400, or 82800 / 90000 on DST days. */
	@Column(name = "period_seconds", nullable = false, updatable = false)
	private long periodSeconds;

	@Column(name = "coverage_pct", nullable = false, updatable = false)
	private double coveragePct;

	@Column(name = "computed_at", nullable = false, updatable = false)
	private Instant computedAt;

	/** {@code true} until persisted or when loaded; drives {@link #isNew()}. */
	@Transient
	@Getter(AccessLevel.NONE)
	private boolean newEntity = true;

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostPersist
	@PostLoad
	void markNotNew() {
		newEntity = false;
	}

	public static DailyMetricRollup of(UUID homeDeviceId, LocalDate localDate, MetricStatistics statistics,
			Instant now) {
		DailyMetricRollup rollup = new DailyMetricRollup();
		rollup.id = UUID.randomUUID();
		rollup.homeDeviceId = homeDeviceId;
		rollup.metric = statistics.getMetric();
		rollup.localDate = localDate;
		rollup.sampleCount = statistics.getSampleCount();
		rollup.min = statistics.getMin();
		rollup.max = statistics.getMax();
		rollup.avg = statistics.getAvg();
		rollup.firstValue = statistics.getFirstValue();
		rollup.firstTime = statistics.getFirstTime();
		rollup.lastValue = statistics.getLastValue();
		rollup.lastTime = statistics.getLastTime();
		rollup.total = statistics.getTotal();
		rollup.coveredSeconds = statistics.getCoveredSeconds();
		rollup.periodSeconds = statistics.getPeriodSeconds();
		rollup.coveragePct = statistics.getCoveragePct();
		rollup.computedAt = now;
		return rollup;
	}

	public MetricStatistics toStatistics() {
		return new MetricStatistics(metric, sampleCount, min, max, avg, firstValue, firstTime, lastValue, lastTime, total,
				coveredSeconds, periodSeconds);
	}
}
