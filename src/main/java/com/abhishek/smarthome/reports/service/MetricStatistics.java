package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.enums.AggregationKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Statistics of one metric over one period: what a {@code DailyMetricRollup} stores for a day and a
 * {@code MetricSummary} reports for a whole range. Periods combine ({@link #combine}) without losing precision:
 * averages are weighted by covered time, totals add up, coverage is covered time over period time.
 */
@Getter
@RequiredArgsConstructor
public final class MetricStatistics {

	private final MetricType metric;
	private final long sampleCount;
	private final @Nullable Double min;
	private final @Nullable Double max;
	private final @Nullable Double avg;
	private final @Nullable Double firstValue;
	private final @Nullable Instant firstTime;
	private final @Nullable Double lastValue;
	private final @Nullable Instant lastTime;
	private final @Nullable Double total;
	private final long coveredSeconds;
	private final long periodSeconds;

	/** No readings in the period. */
	public static MetricStatistics empty(MetricType metric, long periodSeconds) {
		return new MetricStatistics(metric, 0, null, null, null, null, null, null, null, null, 0, periodSeconds);
	}

	/** Share of the period covered by readings, 0–100 (2 decimals). */
	public double getCoveragePct() {
		return periodSeconds == 0 ? 0 : round(100.0 * coveredSeconds / periodSeconds, 2);
	}

	/** The one number per day in a report's daily breakdown: the total for counters and switches, else the average. */
	public @Nullable Double getHeadlineValue() {
		return AggregationKind.of(metric) == AggregationKind.GAUGE ? avg : total;
	}

	/** Statistics of consecutive, non-overlapping periods (e.g. the days of a range) as one period. */
	public static MetricStatistics combine(MetricType metric, List<MetricStatistics> parts) {
		long samples = parts.stream().mapToLong(MetricStatistics::getSampleCount).sum();
		long covered = parts.stream().mapToLong(MetricStatistics::getCoveredSeconds).sum();
		long period = parts.stream().mapToLong(MetricStatistics::getPeriodSeconds).sum();
		Optional<MetricStatistics> first = parts.stream().filter(part -> part.firstTime != null)
				.min(Comparator.comparing(MetricStatistics::getFirstTime));
		Optional<MetricStatistics> last = parts.stream().filter(part -> part.lastTime != null)
				.max(Comparator.comparing(MetricStatistics::getLastTime));
		return new MetricStatistics(metric, samples, minOf(parts), maxOf(parts), weightedAvg(parts),
				first.map(MetricStatistics::getFirstValue).orElse(null), first.map(MetricStatistics::getFirstTime).orElse(null),
				last.map(MetricStatistics::getLastValue).orElse(null), last.map(MetricStatistics::getLastTime).orElse(null),
				sumOfTotals(parts), covered, period);
	}

	private static @Nullable Double minOf(List<MetricStatistics> parts) {
		return parts.stream().map(MetricStatistics::getMin).filter(Objects::nonNull).min(Double::compare).orElse(null);
	}

	private static @Nullable Double maxOf(List<MetricStatistics> parts) {
		return parts.stream().map(MetricStatistics::getMax).filter(Objects::nonNull).max(Double::compare).orElse(null);
	}

	/** Averages weighted by covered seconds (by sample count when nothing is covered). */
	private static @Nullable Double weightedAvg(List<MetricStatistics> parts) {
		List<MetricStatistics> withAvg = parts.stream().filter(part -> part.avg != null).toList();
		if (withAvg.isEmpty()) {
			return null;
		}
		double coveredWeight = withAvg.stream().mapToLong(MetricStatistics::getCoveredSeconds).sum();
		if (coveredWeight > 0) {
			return round(withAvg.stream().mapToDouble(part -> part.avg * part.coveredSeconds).sum() / coveredWeight, 4);
		}
		double sampleWeight = withAvg.stream().mapToLong(MetricStatistics::getSampleCount).sum();
		return round(withAvg.stream().mapToDouble(part -> part.avg * part.sampleCount).sum() / sampleWeight, 4);
	}

	private static @Nullable Double sumOfTotals(List<MetricStatistics> parts) {
		List<Double> totals = parts.stream().map(MetricStatistics::getTotal).filter(Objects::nonNull).toList();
		return totals.isEmpty() ? null : round(totals.stream().mapToDouble(Double::doubleValue).sum(), 4);
	}

	static double round(double value, int decimals) {
		return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
	}
}
