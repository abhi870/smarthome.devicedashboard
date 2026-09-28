package com.abhishek.smarthome.reports.service;

import com.abhishek.smarthome.enums.MetricType;
import com.abhishek.smarthome.reports.enums.AggregationKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Computes {@link MetricStatistics} of one metric over {@code [start, end)} from its readings. Pure function, no I/O.
 *
 * <ul>
 * <li><b>Time weighting</b>: a reading's value holds until the next reading, the end of the period or
 * {@code maxSampleGap} after it, whichever comes first. The held time is its weight in the average and counts as
 * covered; a gap longer than {@code maxSampleGap} counts as missing data.</li>
 * <li><b>Counters</b> (energy, runtime, door openings): total = sum of increases between consecutive readings,
 * starting from the last reading before the period ({@code previous}) so consecutive periods add up exactly. A drop
 * is a counter reset and adds nothing.</li>
 * <li><b>Switch</b>: total = minutes the value was on (≥ 0.5).</li>
 * </ul>
 */
public final class MetricStatisticsCalculator {

	private MetricStatisticsCalculator() {
	}

	/**
	 * @param samples readings with {@code start <= time < end}, any order
	 * @param previous the last reading before {@code start}, used for counters only; may be {@code null}
	 */
	public static MetricStatistics calculate(MetricType metric, List<MetricSample> samples,
			@Nullable MetricSample previous, Instant start, Instant end, Duration maxSampleGap) {
		long periodSeconds = Duration.between(start, end).getSeconds();
		if (samples.isEmpty()) {
			return MetricStatistics.empty(metric, periodSeconds);
		}
		List<MetricSample> sorted = samples.stream().sorted(Comparator.comparing(MetricSample::getTime)).toList();
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double weightedSum = 0;
		long coveredSeconds = 0;
		long onSeconds = 0;
		for (int i = 0; i < sorted.size(); i++) {
			MetricSample sample = sorted.get(i);
			long heldSeconds = heldSeconds(sample, i + 1 < sorted.size() ? sorted.get(i + 1).getTime() : end,
					maxSampleGap);
			coveredSeconds += heldSeconds;
			weightedSum += sample.getValue() * heldSeconds;
			onSeconds += isOn(sample) ? heldSeconds : 0;
			min = Math.min(min, sample.getValue());
			max = Math.max(max, sample.getValue());
		}
		double avg = coveredSeconds > 0 ? weightedSum / coveredSeconds : plainAverage(sorted);
		MetricSample first = sorted.get(0);
		MetricSample last = sorted.get(sorted.size() - 1);
		return new MetricStatistics(metric, sorted.size(), min, max, MetricStatistics.round(avg, 4), first.getValue(),
				first.getTime(), last.getValue(), last.getTime(), total(metric, sorted, previous, onSeconds), coveredSeconds,
				periodSeconds);
	}

	private static long heldSeconds(MetricSample sample, Instant next, Duration maxSampleGap) {
		Instant heldUntil = next.isBefore(sample.getTime().plus(maxSampleGap)) ? next
				: sample.getTime().plus(maxSampleGap);
		return Math.max(0, Duration.between(sample.getTime(), heldUntil).getSeconds());
	}

	private static @Nullable Double total(MetricType metric, List<MetricSample> sorted, @Nullable MetricSample previous,
			long onSeconds) {
		return switch (AggregationKind.of(metric)) {
			case GAUGE -> null;
			case SWITCH -> MetricStatistics.round(onSeconds / 60.0, 4);
			case COUNTER -> MetricStatistics.round(counterIncrease(sorted, previous), 4);
		};
	}

	private static double counterIncrease(List<MetricSample> sorted, @Nullable MetricSample previous) {
		double increase = 0;
		Double prior = previous == null ? null : previous.getValue();
		for (MetricSample sample : sorted) {
			if (prior != null && sample.getValue() > prior) {
				increase += sample.getValue() - prior;
			}
			prior = sample.getValue();
		}
		return increase;
	}

	private static boolean isOn(MetricSample sample) {
		return sample.getValue() >= 0.5;
	}

	private static double plainAverage(List<MetricSample> samples) {
		return samples.stream().mapToDouble(MetricSample::getValue).average().orElse(0);
	}
}
